package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.audit.AuditSealer;
import com.lexvay.audit.AuditService;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 5 for the audit seal (SEC-3): a seal is never changed or removed; the sealer's queue is not a record. */
class Rule5_AuditSealImmutabilityTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired AuditService audit;
    @Autowired AuditSealer sealer;
    @Autowired JdbcClient jdbc;

    UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = tenants.create("seal-immutable").tenantId();
        sealer.sealBatch(tenantId);
    }

    @ParameterizedTest(name = "audit_seal SET {0} is refused")
    @ValueSource(strings = {"row_hash = repeat('a', 64)", "prev_hash = repeat('a', 64)", "seq = seq + 1000", "sealed_at = now()"})
    void aSealCannotBeUpdated(String assignment) {
        assertThatThrownBy(() -> TenantContext.runAs(tenantId, () -> jdbc.sql("UPDATE audit_seal SET " + assignment).update()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Lexvay");
    }

    @Test
    void aSealCannotBeDeleted() {
        assertThatThrownBy(() -> TenantContext.runAs(tenantId, () -> jdbc.sql("DELETE FROM audit_seal").update()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Lexvay");
        assertThat(TenantContext.runAs(tenantId, () -> jdbc.sql("SELECT count(*) FROM audit_seal").query(Long.class).single())).isPositive();
    }

    @Test
    void theQueueIsEmptiedByDeletingFromIt() {
        TenantContext.runAs(tenantId, () -> audit.recordAs(null, "SEAL_TEST", "program", null, Map.of()));

        int removed = TenantContext.runAs(tenantId, () -> jdbc.sql("DELETE FROM audit_unsealed").update());

        assertThat(removed).isOne();
    }
}
