package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.audit.AuditSealer;
import com.lexvay.audit.AuditService;
import com.lexvay.audit.AuditVerificationService;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Product rule 7 for the audit seal (SEC-3): one tenant's seals and queue are invisible to another, no tenant sees
 * nothing, and the sealer's worker flag opens the queue for reading and nothing else.
 */
class Rule7_AuditSealIsolationTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired AuditService audit;
    @Autowired AuditSealer sealer;
    @Autowired AuditVerificationService verification;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate transaction;

    UUID a;
    UUID b;
    UUID sealedEntryOfA;

    @BeforeEach
    void setUp() {
        a = tenants.create("seal-a").tenantId();
        b = tenants.create("seal-b").tenantId();
        sealer.sealBatch(a);
        sealedEntryOfA = TenantContext.runAs(a, () -> jdbc.sql("SELECT audit_id FROM audit_seal LIMIT 1").query(UUID.class).single());
        TenantContext.runAs(a, () -> audit.recordAs(null, "SEAL_TEST", "program", null, Map.of()));
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    @Test
    void anotherTenantSeesNeitherSealsNorQueue() {
        TenantContext.runAs(b, () -> {
            assertThat(count("SELECT count(*) FROM audit_seal WHERE tenant_id = '" + a + "'")).isZero();
            assertThat(count("SELECT count(*) FROM audit_unsealed WHERE tenant_id = '" + a + "'")).isZero();
            assertThat(count("SELECT count(*) FROM audit_unsealed WHERE tenant_id = '" + b + "'")).isPositive();
        });
        TenantContext.runAs(a, () -> {
            assertThat(count("SELECT count(*) FROM audit_seal WHERE tenant_id = '" + a + "'")).isPositive();
            assertThat(count("SELECT count(*) FROM audit_seal WHERE tenant_id = '" + b + "'")).isZero();
        });
    }

    @Test
    void noTenantFailsClosed() {
        TenantContext.runWithoutTenant(() -> {
            assertThat(count("SELECT count(*) FROM audit_seal")).isZero();
            assertThat(count("SELECT count(*) FROM audit_unsealed")).isZero();
            return null;
        });
    }

    @Test
    void aTenantCannotWriteSealsOrQueueRowsForAnotherTenant() {
        TenantContext.runAs(b, () -> {
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO audit_seal (tenant_id, seq, audit_id, prev_hash, row_hash) "
                    + "VALUES (:a, 1000, :entry, repeat('0', 64), repeat('1', 64))")
                    .param("a", a).param("entry", sealedEntryOfA).update()).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO audit_unsealed (audit_id, tenant_id, created_at) VALUES (:entry, :a, now())")
                    .param("a", a).param("entry", sealedEntryOfA).update()).isInstanceOf(DataAccessException.class);
        });
    }

    @Test
    void verifyingReadsOnlyTheCallersSeals() {
        long mine = TenantContext.runAs(a, () -> verification.verify().sealed());
        long theirs = TenantContext.runAs(b, () -> verification.verify().sealed());

        assertThat(mine).isEqualTo(TenantContext.runAs(a, () -> count("SELECT count(*) FROM audit_seal")));
        assertThat(theirs).isZero();
    }

    @Test
    void theWorkerFlagListsTheQueueAcrossTenantsButOpensNothingElse() {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            assertThat(count("SELECT count(DISTINCT tenant_id) FROM audit_unsealed WHERE tenant_id IN ('" + a + "', '" + b + "')"))
                    .isEqualTo(2);
            assertThat(count("SELECT count(*) FROM audit_seal")).as("seals stay closed").isZero();
            assertThat(count("SELECT count(*) FROM audit_log")).as("the log stays closed").isZero();
            assertThat(jdbc.sql("DELETE FROM audit_unsealed").update()).as("the flag does not allow deleting").isZero();
        });
    }
}
