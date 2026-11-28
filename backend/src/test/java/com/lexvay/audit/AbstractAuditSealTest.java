package com.lexvay.audit;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * What the audit seal tests share: entries to seal, the sealer, the verification, and a way to do what only a database
 * superuser can, which is the attack the seal exists to reveal. The sealer's timer is off in tests
 * ({@link AbstractDatabaseTest}), so sealing happens only where a test asks for it.
 */
abstract class AbstractAuditSealTest extends AbstractDatabaseTest {

    @Autowired protected TestTenants tenants;
    @Autowired protected AuditService audit;
    @Autowired protected AuditSealer sealer;
    @Autowired protected AuditVerificationService verification;
    @Autowired protected JdbcClient jdbc;

    /** Writes {@code count} audit entries for the tenant through the application's own path. */
    protected void record(UUID tenantId, int count) {
        TenantContext.runAs(tenantId, () -> {
            for (int n = 1; n <= count; n++) {
                audit.recordAs(null, "SEAL_TEST", "program", UUID.randomUUID(), Map.of("n", n));
            }
        });
    }

    /** Seals everything queued for the tenant, batch by batch, and returns how many entries that was. */
    protected int sealAll(UUID tenantId) {
        int sealed = 0;
        for (int batch; (batch = sealer.sealBatch(tenantId)) > 0; ) {
            sealed += batch;
        }
        return sealed;
    }

    protected AuditVerification verify(UUID tenantId) {
        return TenantContext.runAs(tenantId, verification::verify);
    }

    protected <T> T inTenant(UUID tenantId, Class<T> type, String sql, Object... args) {
        return TenantContext.runAs(tenantId, () -> {
            var statement = jdbc.sql(sql);
            for (int i = 0; i < args.length; i++) {
                statement = statement.param(i + 1, args[i]);
            }
            return statement.query(type).single();
        });
    }

    protected UUID auditIdOfSeq(UUID tenantId, long seq) {
        return inTenant(tenantId, UUID.class, "SELECT audit_id FROM audit_seal WHERE seq = ?", seq);
    }

    /**
     * Runs {@code sql} as the database superuser with the immutability trigger of {@code table} switched off, as someone
     * with that access (or a restore from an altered backup) could, and switches it on again. Returns the rows changed.
     */
    protected int asSuperuser(String table, String trigger, String sql, Object... args) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.createStatement().execute("ALTER TABLE " + table + " DISABLE TRIGGER " + trigger);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    statement.setObject(i + 1, args[i]);
                }
                return statement.executeUpdate();
            } finally {
                connection.createStatement().execute("ALTER TABLE " + table + " ENABLE TRIGGER " + trigger);
            }
        }
    }
}
