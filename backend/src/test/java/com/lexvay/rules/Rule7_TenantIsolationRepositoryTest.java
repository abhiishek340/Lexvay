package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.SchemaFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.program.ProgramRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 7 through direct repository calls: the database itself enforces the boundary. */
class Rule7_TenantIsolationRepositoryTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired SchemaFixtures fixtures;
    @Autowired ProgramRepository programs;
    @Autowired JdbcClient jdbc;

    SchemaFixtures.Chain a;
    TestTenants.TenantWithUsers b;

    @BeforeEach
    void setUp() {
        var tenantA = tenants.create("alpha");
        a = fixtures.insertChain(tenantA.tenantId(), tenantA.admin().id());
        b = tenants.create("beta");
    }

    @Test
    void tenantBCannotReadTenantAsRows() {
        TenantContext.runAs(b.tenantId(), () -> {
            assertThat(programs.findById(a.programId())).isEmpty();
            assertThat(programs.findAll()).isEmpty();
            for (String table : new String[] {"document", "document_page", "rule_version", "requirement", "assessment",
                    "evidence_span", "mapping", "review_event", "determination", "audit_log", "llm_call", "app_user"}) {
                Long count = jdbc.sql("SELECT count(*) FROM " + table + " WHERE tenant_id = :t")
                        .param("t", a.tenantId()).query(Long.class).single();
                assertThat(count).as(table).isZero();
            }
        });
        TenantContext.runAs(a.tenantId(), () -> assertThat(programs.findById(a.programId())).isPresent());
    }

    @Test
    void tenantBUpdateOfTenantAsRowAffectsNothing() {
        int updated = TenantContext.runAs(b.tenantId(), () -> programs.rename(a.programId(), "Hijacked"));
        assertThat(updated).isZero();
        TenantContext.runAs(a.tenantId(), () ->
                assertThat(programs.findById(a.programId()).orElseThrow().name()).isEqualTo("Fixture program"));
    }

    @Test
    void tenantBCannotInsertARowCarryingTenantAsId() {
        assertThatThrownBy(() -> TenantContext.runAs(b.tenantId(), () ->
                programs.insert(UUID.randomUUID(), a.tenantId(), "Smuggled", "LPC", "2026-27", b.admin().id())))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("row-level security");
    }
}
