package com.lexvay.tenant;

import com.lexvay.auth.LexvayUser;
import com.lexvay.auth.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AppUserRepository {

    private static final RowMapper<LexvayUser> USER = (rs, i) -> new LexvayUser(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("email"),
            rs.getString("display_name"),
            Role.valueOf(rs.getString("role")),
            rs.getString("password_hash"));

    private static final String COLUMNS = "id, tenant_id, email, display_name, role, password_hash";

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
     * Login happens before the tenant is known. The lookup flag is transaction-local and is honoured
     * only by the SELECT policy on app_user (D-034), so this is the one query that may cross tenants.
     */
    @Transactional
    public Optional<LexvayUser> findForLogin(String email) {
        if (email.indexOf('\u0000') >= 0) {
            return Optional.empty(); // PostgreSQL text cannot hold NUL, so no account has such an email
        }
        jdbc.sql("SELECT set_config('lexvay.auth_lookup', 'on', true)").query().singleRow();
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE email = :email")
                .param("email", email.toLowerCase())
                .query(USER)
                .optional();
    }

    public Optional<LexvayUser> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = :id")
                .param("id", id).query(USER).optional();
    }

    public List<LexvayUser> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user ORDER BY email").query(USER).list();
    }
}
