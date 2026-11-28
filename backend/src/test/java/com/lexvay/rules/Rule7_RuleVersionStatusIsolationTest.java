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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 7 for legal status (RG-1): another tenant, or no tenant, sees and writes nothing, through the table and the view. */
class Rule7_RuleVersionStatusIsolationTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired SchemaFixtures fixtures;
    @Autowired RuleVersionRepository ruleVersions;
    @Autowired JdbcClient jdbc;

    SchemaFixtures.Chain a;
    TestTenants.TenantWithUsers tenantA;
    TestTenants.TenantWithUsers b;

    @BeforeEach
    void setUp() {
        tenantA = tenants.create("status-a");
        b = tenants.create("status-b");
        a = fixtures.insertChain(tenantA.tenantId(), tenantA.admin().id());
        TenantContext.runAs(tenantA.tenantId(), () -> ruleVersions.insertStatus(tenantA.tenantId(), a.ruleVersionId(),
                new LegalPosition(LegalStatus.REPEALED, LocalDate.of(2026, 1, 1), null, null, null, "Fictional notice"), tenantA.admin().id()));
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void anotherTenantSeesNeitherTheStatusRowsNorTheVersionThroughTheView() {
        TenantContext.runAs(b.tenantId(), () -> {
            assertThat(count("rule_version_status")).isZero();
            assertThat(count("rule_version_current")).isZero();
            assertThat(ruleVersions.findById(a.ruleVersionId())).isEmpty();
            return null;
        });
        TenantContext.runAs(tenantA.tenantId(), () -> {
            assertThat(count("rule_version_status")).isOne();
            assertThat(ruleVersions.findById(a.ruleVersionId()).orElseThrow().legal().status()).isEqualTo(LegalStatus.REPEALED);
            return null;
        });
    }

    @Test
    void noTenantFailsClosed() {
        TenantContext.runWithoutTenant(() -> {
            assertThat(count("rule_version_status")).isZero();
            assertThat(count("rule_version_current")).isZero();
            assertThatThrownBy(() -> ruleVersions.insertStatus(tenantA.tenantId(), a.ruleVersionId(),
                    LegalPosition.unknown(), tenantA.admin().id())).isInstanceOf(DataAccessException.class);
            return null;
        });
    }

    @Test
    void aTenantCannotWriteAStatusRowForAnotherTenant() {
        TenantContext.runAs(b.tenantId(), () -> {
            assertThatThrownBy(() -> ruleVersions.insertStatus(tenantA.tenantId(), a.ruleVersionId(), LegalPosition.unknown(), b.admin().id()))
                    .isInstanceOf(DataAccessException.class);
            return null;
        });
        assertThat(TenantContext.runAs(tenantA.tenantId(), () -> count("rule_version_status"))).isOne();
    }
}
