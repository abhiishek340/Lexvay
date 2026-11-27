package com.lexvay.tenant;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** The setup codes of {@code user_setup_token} (D-372). Only hashes pass through here, never a code. */
@Repository
public class SetupTokenRepository {

    /** The open code a hash names, and the user it belongs to. */
    public record OpenCode(UUID id, UUID tenantId, UUID userId, String email, String tokenHash) {
    }

    private final JdbcClient jdbc;

    public SetupTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, UUID userId, String tokenHash, OffsetDateTime expiresAt, UUID createdBy) {
        jdbc.sql("INSERT INTO user_setup_token (id, tenant_id, user_id, token_hash, expires_at, created_by) "
                        + "VALUES (:id, :tenantId, :userId, :tokenHash, :expiresAt, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("userId", userId)
                .param("tokenHash", tokenHash).param("expiresAt", expiresAt).param("createdBy", createdBy)
                .update();
    }

    /** Marks every unused code of the user as used, so only the one issued next works. */
    public int closeOpenCodes(UUID userId) {
        return jdbc.sql("UPDATE user_setup_token SET used_at = now() WHERE user_id = :userId AND used_at IS NULL")
                .param("userId", userId).update();
    }

    /**
     * The code that was issued with this hash, if it is unused and unexpired. The tenant is not known yet, so the
     * lookup runs under the transaction-local {@code lexvay.auth_lookup} flag, which opens SELECT on this table and on
     * app_user and nothing else (D-034). Unknown, used and expired give the same empty answer.
     */
    @Transactional
    public Optional<OpenCode> findOpen(String tokenHash) {
        jdbc.sql("SELECT set_config('lexvay.auth_lookup', 'on', true)").query().singleRow();
        return jdbc.sql("SELECT t.id, t.tenant_id, t.user_id, u.email, t.token_hash "
                        + "FROM user_setup_token t JOIN app_user u ON u.id = t.user_id "
                        + "WHERE t.token_hash = :tokenHash AND t.used_at IS NULL AND t.expires_at > now()")
                .param("tokenHash", tokenHash)
                .query((rs, i) -> new OpenCode(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("user_id", UUID.class), rs.getString("email"), rs.getString("token_hash")))
                .optional();
    }

    /** Claims a code. False when somebody used it, or it expired, since it was found: a code works once. */
    public boolean markUsed(UUID id) {
        return jdbc.sql("UPDATE user_setup_token SET used_at = now() WHERE id = :id AND used_at IS NULL AND expires_at > now()")
                .param("id", id).update() == 1;
    }
}
