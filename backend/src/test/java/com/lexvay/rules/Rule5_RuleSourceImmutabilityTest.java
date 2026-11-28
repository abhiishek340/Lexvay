package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.rule.source.CheckOutcome;
import com.lexvay.rule.source.CreateRuleSourceRequest;
import com.lexvay.rule.source.RuleSourceRepository;
import com.lexvay.rule.source.RuleSourceService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 5 for rule sources: checks are never changed, a source only changes how it is monitored. */
class Rule5_RuleSourceImmutabilityTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired RuleSourceService service;
    @Autowired RuleSourceRepository repository;
    @Autowired JdbcClient jdbc;

    TestTenants.TenantWithUsers tenant;
    UUID sourceId;
    UUID checkId;

    @BeforeEach
    void setUp() {
        tenant = tenants.create("source-immutable");
        TenantContext.runAs(tenant.tenantId(), () -> {
            sourceId = service.register(new CreateRuleSourceRequest("CA", "LICENSE", "Source", "https://rules.example.test/a.pdf", 24));
            checkId = repository.insertCheck(tenant.tenantId(), sourceId, OffsetDateTime.now(), CheckOutcome.UNCHANGED, 200, "a".repeat(64), null, null, null);
        });
    }

    private int update(String sql) {
        return TenantContext.runAs(tenant.tenantId(), () -> jdbc.sql(sql).param("id", sourceId).param("check", checkId).update());
    }

    @ParameterizedTest(name = "UPDATE rule_source SET {0}")
    @ValueSource(strings = {"url = 'https://rules.example.test/b.pdf'", "label = 'Renamed'", "license_type = 'OTHER'", "check_interval_hours = 48"})
    void whatIsMonitoredCannotBeChanged(String assignment) {
        assertThatThrownBy(() -> update("UPDATE rule_source SET " + assignment + " WHERE id = :id"))
                .hasMessageContaining("only active, last_checked_at, last_content_hash, next_check_at, consecutive_failures may change");
    }

    @Test
    void howItIsMonitoredCanChange() {
        assertThat(update("UPDATE rule_source SET active = false WHERE id = :id")).isEqualTo(1);
        assertThat(update("UPDATE rule_source SET last_checked_at = now(), last_content_hash = '" + "b".repeat(64) + "' WHERE id = :id")).isEqualTo(1);
        assertThat(update("UPDATE rule_source SET next_check_at = now() + interval '1 hour', consecutive_failures = 3 WHERE id = :id")).isEqualTo(1);
    }

    @Test
    void aSourceCannotBeDeleted() {
        assertThatThrownBy(() -> update("DELETE FROM rule_source WHERE id = :id")).hasMessageContaining("immutable");
    }

    @Test
    void aCheckCannotBeUpdatedOrDeleted() {
        assertThatThrownBy(() -> update("UPDATE rule_source_check SET outcome = 'CHANGED' WHERE id = :check")).hasMessageContaining("immutable");
        assertThatThrownBy(() -> update("UPDATE rule_source_check SET detail = 'edited' WHERE id = :check")).hasMessageContaining("immutable");
        assertThatThrownBy(() -> update("DELETE FROM rule_source_check WHERE id = :check")).hasMessageContaining("immutable");
    }

    @Test
    void onlyAChangedCheckCarriesADocumentAndAVersion() {
        TenantContext.runAs(tenant.tenantId(), () -> {
            assertThatThrownBy(() -> repository.insertCheck(tenant.tenantId(), sourceId, OffsetDateTime.now(), CheckOutcome.CHANGED, 200, null, null, null, null))
                    .hasMessageContaining("rule_source_check_changed_has_both");
        });
    }
}
