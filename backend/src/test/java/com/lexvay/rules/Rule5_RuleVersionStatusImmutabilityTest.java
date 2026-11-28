package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.SchemaFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.rule.LegalPosition;
import com.lexvay.rule.LegalStatus;
import com.lexvay.rule.RuleVersionRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 5 for legal status (RG-1): a correction is a new row, a row is never changed or removed (D-552). */
class Rule5_RuleVersionStatusImmutabilityTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired SchemaFixtures fixtures;
    @Autowired RuleVersionRepository ruleVersions;
    @Autowired JdbcClient jdbc;

    SchemaFixtures.Chain chain;
    TestTenants.TenantWithUsers tenant;

    @BeforeEach
    void setUp() {
        tenant = tenants.create("status-immutable");
        chain = fixtures.insertChain(tenant.tenantId(), tenant.admin().id());
        TenantContext.runAs(tenant.tenantId(), () -> ruleVersions.insertStatus(tenant.tenantId(), chain.ruleVersionId(),
                new LegalPosition(LegalStatus.SUPERSEDED, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1), null, null, "Fictional notice"),
                tenant.admin().id()));
    }

    private int update(String sql) {
        return TenantContext.runAs(tenant.tenantId(), () -> jdbc.sql(sql).param("id", chain.ruleVersionId()).update());
    }

    @ParameterizedTest(name = "UPDATE rule_version_status SET {0}")
    @ValueSource(strings = {"legal_status = 'IN_FORCE'", "effective_from = DATE '2020-01-01'", "effective_to = NULL",
            "dates_basis = 'edited'", "recorded_by = NULL", "recorded_at = now()"})
    void aRowCannotBeUpdated(String assignment) {
        assertThatThrownBy(() -> update("UPDATE rule_version_status SET " + assignment + " WHERE rule_version_id = :id"))
                .hasMessageContaining("immutable");
    }

    @Test
    void aRowCannotBeDeleted() {
        assertThatThrownBy(() -> update("DELETE FROM rule_version_status WHERE rule_version_id = :id")).hasMessageContaining("immutable");
    }

    @Test
    void theDatabaseRefusesADateWithoutItsBasisAndAnEndBeforeItsStart() {
        TenantContext.runAs(tenant.tenantId(), () -> {
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO rule_version_status (tenant_id, rule_version_id, legal_status, effective_from, recorded_by) "
                    + "VALUES (:t, :id, 'IN_FORCE', DATE '2026-01-01', :u)").param("t", tenant.tenantId()).param("id", chain.ruleVersionId())
                    .param("u", tenant.admin().id()).update()).hasMessageContaining("rule_version_status_dates_need_basis");
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO rule_version_status (tenant_id, rule_version_id, legal_status, effective_from, "
                    + "effective_to, dates_basis, recorded_by) VALUES (:t, :id, 'IN_FORCE', DATE '2026-02-01', DATE '2026-01-01', 'x', :u)")
                    .param("t", tenant.tenantId()).param("id", chain.ruleVersionId()).param("u", tenant.admin().id()).update())
                    .hasMessageContaining("rule_version_status_period");
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO rule_version_status (tenant_id, rule_version_id, legal_status, recorded_by) "
                    + "VALUES (:t, :id, 'COMPLIANT', :u)").param("t", tenant.tenantId()).param("id", chain.ruleVersionId())
                    .param("u", tenant.admin().id()).update()).hasMessageContaining("legal_status");
            return null;
        });
        assertThat(TenantContext.runAs(tenant.tenantId(), () -> jdbc.sql("SELECT count(*) FROM rule_version_status")
                .query(Long.class).single())).isEqualTo(1);
    }
}
