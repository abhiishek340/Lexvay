package com.lexvay.rules;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.rule.source.CheckOutcome;
import com.lexvay.rule.source.CreateRuleSourceRequest;
import com.lexvay.rule.source.RuleSourceRepository;
import com.lexvay.rule.source.RuleSourceService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Product rule 7 for rule sources and their checks: another tenant, or no tenant, sees and changes nothing. */
class Rule7_RuleSourceIsolationTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired RuleSourceService service;
    @Autowired RuleSourceRepository repository;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvcTester mvc;

    TestTenants.TenantWithUsers a;
    TestTenants.TenantWithUsers b;
    UUID sourceOfA;

    @BeforeEach
    void setUp() {
        a = tenants.create("sources-a");
        b = tenants.create("sources-b");
        sourceOfA = TenantContext.runAs(a.tenantId(), () -> {
            UUID id = service.register(new CreateRuleSourceRequest("CA", "LICENSE", "A's source", "https://rules.example.test/a.pdf", 24));
            repository.insertCheck(a.tenantId(), id, CheckOutcome.FETCH_FAILED, 500, null, "The server answered HTTP 500", null, null);
            return id;
        });
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void anotherTenantSeesNeitherTheSourceNorItsChecks() {
        TenantContext.runAs(b.tenantId(), () -> {
            assertThat(count("rule_source")).isZero();
            assertThat(count("rule_source_check")).isZero();
            assertThat(repository.list()).isEmpty();
            assertThat(repository.findById(sourceOfA)).isEmpty();
            assertThat(repository.checks(sourceOfA, 10)).isEmpty();
        });
        TenantContext.runAs(a.tenantId(), () -> {
            assertThat(repository.list()).hasSize(1);
            assertThat(repository.checks(sourceOfA, 10).orElseThrow()).hasSize(1);
        });
    }

    @Test
    void noTenantFailsClosed() {
        TenantContext.runWithoutTenant(() -> {
            assertThat(count("rule_source")).isZero();
            assertThat(count("rule_source_check")).isZero();
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO rule_source (tenant_id, jurisdiction_id, license_type, label, url, check_interval_hours) "
                    + "SELECT :t, id, 'L', 'x', 'https://rules.example.test/x', 1 FROM jurisdiction WHERE code = 'CA'")
                    .param("t", a.tenantId()).update()).isInstanceOf(DataAccessException.class);
            return null;
        });
    }

    @Test
    void aTenantCannotWriteRowsForAnotherTenant() {
        TenantContext.runAs(b.tenantId(), () -> {
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO rule_source (tenant_id, jurisdiction_id, license_type, label, url, check_interval_hours) "
                    + "SELECT :t, id, 'L', 'x', 'https://rules.example.test/x', 1 FROM jurisdiction WHERE code = 'CA'")
                    .param("t", a.tenantId()).update()).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> repository.insertCheck(a.tenantId(), sourceOfA, CheckOutcome.UNCHANGED, 200, null, null, null, null))
                    .isInstanceOf(DataAccessException.class);
            assertThat(jdbc.sql("UPDATE rule_source SET active = false WHERE id = :id").param("id", sourceOfA).update()).isZero();
            return null;
        });
        assertThat(TenantContext.runAs(a.tenantId(), () -> repository.findById(sourceOfA)).orElseThrow().active()).isTrue();
    }

    @Test
    void theWorkerFlagIsNotSomethingATenantSessionHas() {
        TenantContext.runAs(b.tenantId(), () -> {
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO job (tenant_id, type, payload) VALUES (:t, 'CHECK_RULE_SOURCE', '{}'::jsonb)")
                    .param("t", a.tenantId()).update()).isInstanceOf(DataAccessException.class);
            return null;
        });
    }

    @Test
    void theApiAnswersNotFoundForAnotherTenantsSource() {
        String base = "/api/v1/rule-sources/" + sourceOfA;

        assertThat(mvc.get().uri("/api/v1/rule-sources").with(user(b.admin())).exchange()).bodyText().isEqualTo("[]");
        assertThat(mvc.get().uri(base + "/checks").with(user(b.admin())).exchange()).hasStatus(404);
        assertThat(mvc.post().uri(base + "/check").with(user(b.admin())).with(csrf()).exchange()).hasStatus(404);
        assertThat(mvc.put().uri(base + "/active").with(user(b.admin())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}").exchange()).hasStatus(404);
        assertThat(TenantContext.runAs(a.tenantId(), () -> repository.findById(sourceOfA)).orElseThrow().active()).isTrue();
    }
}
