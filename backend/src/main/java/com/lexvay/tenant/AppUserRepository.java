package com.lexvay.tenant;

import com.lexvay.auth.AccessState;
import com.lexvay.auth.LexvayUser;
import com.lexvay.auth.Role;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AppUserRepository {

    /** A user as the administration screens see it: no hash, only whether a password is set. */
    public record Account(UUID id, String displayName, Role role, String email, boolean active,
                          OffsetDateTime createdAt, boolean passwordSet) {
    }

    private static final RowMapper<LexvayUser> USER = (rs, i) -> new LexvayUser(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("email"),
            rs.getString("display_name"),
            Role.valueOf(rs.getString("role")),
            rs.getString("password_hash"),
            rs.getInt("security_stamp"));

    private static final RowMapper<Account> ACCOUNT = (rs, i) -> new Account(
            rs.getObject("id", UUID.class),
            rs.getString("display_name"),
            Role.valueOf(rs.getString("role")),
            rs.getString("email"),
            rs.getBoolean("active"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getBoolean("password_set"));

    private static final String COLUMNS = "id, tenant_id, email, display_name, role, password_hash, security_stamp";

    private static final String SELECT_ACCOUNT = "SELECT id, display_name, role, email, active, created_at, "
            + "password_hash IS NOT NULL AS password_set FROM app_user";

    private final JdbcClient jdbc;

    public AppUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, String email, String displayName, Role role, String passwordHash,
                       UUID createdBy) {
        jdbc.sql("INSERT INTO app_user (id, tenant_id, email, display_name, role, password_hash, created_by) "
                        + "VALUES (:id, :tenantId, :email, :displayName, :role, :passwordHash, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("email", email.toLowerCase())
                .param("displayName", displayName).param("role", role.name())
                .param("passwordHash", passwordHash).param("createdBy", createdBy)
                .update();
    }

    /**
     * Adds a user who has no password yet. Emails are unique across tenants, so an address that any tenant already
     * uses adds nothing and returns false.
     */
    public boolean insertWithoutPassword(UUID id, UUID tenantId, String email, String displayName, Role role,
                                         UUID createdBy) {
        return jdbc.sql("INSERT INTO app_user (id, tenant_id, email, display_name, role, created_by) "
                        + "VALUES (:id, :tenantId, :email, :displayName, :role, :createdBy) "
                        + "ON CONFLICT (email) DO NOTHING")
                .param("id", id).param("tenantId", tenantId).param("email", email.toLowerCase())
                .param("displayName", displayName).param("role", role.name()).param("createdBy", createdBy)
                .update() == 1;
    }

    /**
     * Login happens before the tenant is known. The lookup flag is transaction-local and is honoured
     * only by the SELECT policy on app_user (D-034), so this is the one query that may cross tenants.
     * A deactivated user is not found: the caller cannot tell it from an unknown email.
     */
    @Transactional
    public Optional<LexvayUser> findForLogin(String email) {
        if (email.indexOf('\u0000') >= 0) {
            return Optional.empty(); // PostgreSQL text cannot hold NUL, so no account has such an email
        }
        jdbc.sql("SELECT set_config('lexvay.auth_lookup', 'on', true)").query().singleRow();
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE email = :email AND active")
                .param("email", email.toLowerCase())
                .query(USER)
                .optional();
    }

    public Optional<LexvayUser> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = :id")
                .param("id", id).query(USER).optional();
    }

    /** The values a session is checked against (D-374): one statement. */
    public Optional<AccessState> findAccess(UUID id) {
        return jdbc.sql("SELECT active, role, security_stamp FROM app_user WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> new AccessState(rs.getBoolean("active"), Role.valueOf(rs.getString("role")),
                        rs.getInt("security_stamp")))
                .optional();
    }

    /** Every user of the caller's tenant (row-level security scopes it), by display name. */
    public List<Account> listAccounts() {
        return jdbc.sql(SELECT_ACCOUNT + " ORDER BY display_name, email, id").query(ACCOUNT).list();
    }

    public Optional<Account> findAccount(UUID id) {
        return jdbc.sql(SELECT_ACCOUNT + " WHERE id = :id").param("id", id).query(ACCOUNT).optional();
    }

    /** Reads the user and holds its row until the transaction ends, so a concurrent change waits and then sees this one. */
    public Optional<Account> lockAccount(UUID id) {
        return jdbc.sql(SELECT_ACCOUNT + " WHERE id = :id FOR UPDATE").param("id", id).query(ACCOUNT).optional();
    }

    /**
     * Holds every active administrator of the tenant until the transaction ends. Two changes that each leave "the
     * other" administrator in place would otherwise both pass the last-administrator check. Rows are taken in id
     * order, so two callers cannot wait for each other.
     */
    public List<UUID> lockActiveAdministrators() {
        return jdbc.sql("SELECT id FROM app_user WHERE role = :admin AND active ORDER BY id FOR UPDATE")
                .param("admin", Role.ADMIN.name()).query(UUID.class).list();
    }

    public void updateRole(UUID id, Role role) {
        jdbc.sql("UPDATE app_user SET role = :role, security_stamp = security_stamp + 1 WHERE id = :id")
                .param("role", role.name()).param("id", id).update();
    }

    public void updateActive(UUID id, boolean active) {
        jdbc.sql("UPDATE app_user SET active = :active, security_stamp = security_stamp + 1 WHERE id = :id")
                .param("active", active).param("id", id).update();
    }

    /**
     * Replaces a hash by another hash of the same password (D-401). The stamp does not move: nobody's session ends.
     * Returns false, and changes nothing, when the stored hash is no longer {@code expectedHash}, which means the
     * password was set again in the meantime and the new hash would overwrite it.
     */
    public boolean rehashPassword(UUID id, String expectedHash, String newHash) {
        return jdbc.sql("UPDATE app_user SET password_hash = :newHash WHERE id = :id AND password_hash = :expectedHash")
                .param("newHash", newHash).param("id", id).param("expectedHash", expectedHash)
                .update() == 1;
    }

    /** Sets the hash and ends every session the user has (the stamp moves). */
    public void setPassword(UUID id, String passwordHash) {
        jdbc.sql("UPDATE app_user SET password_hash = :hash, security_stamp = security_stamp + 1 WHERE id = :id")
                .param("hash", passwordHash).param("id", id).update();
    }
}
