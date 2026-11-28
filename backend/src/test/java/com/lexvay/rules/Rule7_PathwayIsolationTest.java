package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.common.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 7 for graduation paths: another tenant, or no tenant, sees and writes nothing, and the row-level policies fail closed. */
class Rule7_PathwayIsolationTest extends AbstractAssessmentTest {

    static final List<String> TABLES = List.of("pathway_group", "pathway_group_course", "pathway_group_retirement");

    @Autowired JdbcClient jdbc;

    UUID programId;
    UUID groupId;
    UUID courseId;
    UUID otherTenantId;

    @BeforeEach
    void setUp() {
        programId = fx.program(t, List.of("CA"));
        fx.courseOnly(t, programId, "COUN 605", true, "3");
        courseId = fx.courseIds(t, programId).get("COUN 605");
        groupId = UUID.fromString(fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, List.of(courseId))
                .get("groups").get(0).get("id").asString());
        fx.retirePathwayGroup(t, groupId, "Retired for the test");
        otherTenantId = tenants.create("pathway-isolation").tenantId();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void theOwnerTenantSeesItsRows() {
        TenantContext.runAs(t.tenantId(), () -> {
            TABLES.forEach(table -> assertThat(count(table)).as(table).isEqualTo(1));
            return null;
        });
    }

    @Test
    void anotherTenantSeesNoRow() {
        TenantContext.runAs(otherTenantId, () -> {
            TABLES.forEach(table -> assertThat(count(table)).as(table).isZero());
            return null;
        });
    }

    @Test
    void noTenantSeesNoRowAndWritesNone() {
        TenantContext.runWithoutTenant(() -> {
            TABLES.forEach(table -> assertThat(count(table)).as(table).isZero());
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO pathway_group_retirement (group_id, tenant_id, reason) VALUES (:g, :t, 'x')")
                    .param("g", groupId).param("t", t.tenantId()).update()).isInstanceOf(DataAccessException.class);
            return null;
        });
    }

    @Test
    void aTenantCannotWriteRowsForAnotherTenant() {
        TenantContext.runAs(otherTenantId, () -> {
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO pathway_group (tenant_id, program_id, catalog_year, label, kind) VALUES (:t, :p, '2026-27', 'x', 'ALL_OF')")
                    .param("t", t.tenantId()).param("p", programId).update()).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO pathway_group_course (group_id, course_id, tenant_id) VALUES (:g, :c, :t)")
                    .param("g", groupId).param("c", courseId).param("t", t.tenantId()).update()).isInstanceOf(DataAccessException.class);
            return null;
        });
    }

    @Test
    void everyPathwayTableForcesRowLevelSecurityWithAPolicy() {
        for (String table : TABLES) {
            var row = jdbc.sql("SELECT c.relrowsecurity AS enabled, c.relforcerowsecurity AS forced, "
                            + "(SELECT count(*) FROM pg_policies p WHERE p.tablename = c.relname) AS policies FROM pg_class c WHERE c.relname = :t")
                    .param("t", table).query().singleRow();
            assertThat(row.get("enabled")).as(table).isEqualTo(true);
            assertThat(row.get("forced")).as(table).isEqualTo(true);
            assertThat(((Number) row.get("policies")).intValue()).as(table).isGreaterThanOrEqualTo(1);
        }
    }
}
