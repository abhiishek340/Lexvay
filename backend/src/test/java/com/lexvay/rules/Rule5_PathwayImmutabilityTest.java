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
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 5 for graduation paths: a group is never edited or deleted; a correction retires it and adds another. */
class Rule5_PathwayImmutabilityTest extends AbstractAssessmentTest {

    @Autowired JdbcClient jdbc;

    UUID groupId;
    UUID courseId;

    @BeforeEach
    void setUp() {
        UUID programId = fx.program(t, List.of("CA"));
        fx.courseOnly(t, programId, "COUN 605", true, "3");
        courseId = fx.courseIds(t, programId).get("COUN 605");
        var made = fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, List.of(courseId));
        groupId = UUID.fromString(made.get("groups").get(0).get("id").asString());
        assertThat(fx.retirePathwayGroup(t, groupId, "Superseded by a corrected group")).hasStatus(200);
    }

    private void refuses(String sql) {
        assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () -> jdbc.sql(sql).param("g", groupId).param("c", courseId).update()))
                .hasMessageContaining("immutable");
    }

    @Test
    void aGroupCannotBeChangedOrDeleted() {
        refuses("UPDATE pathway_group SET label = 'Renamed' WHERE id = :g");
        refuses("UPDATE pathway_group SET kind = 'CHOOSE_COUNT', min_count = 1 WHERE id = :g");
        refuses("DELETE FROM pathway_group WHERE id = :g");
    }

    @Test
    void theMembersOfAGroupCannotBeChangedOrDeleted() {
        refuses("UPDATE pathway_group_course SET course_id = course_id WHERE group_id = :g");
        refuses("DELETE FROM pathway_group_course WHERE group_id = :g AND course_id = :c");
    }

    @Test
    void aRetirementCannotBeChangedOrUndone() {
        refuses("UPDATE pathway_group_retirement SET reason = 'Something else' WHERE group_id = :g");
        refuses("DELETE FROM pathway_group_retirement WHERE group_id = :g");
        TenantContext.runAs(t.tenantId(), () -> {
            assertThat(jdbc.sql("SELECT count(*) FROM pathway_group_retirement WHERE group_id = :g").param("g", groupId).query(Long.class).single()).isEqualTo(1);
            return null;
        });
    }

    @Test
    void aGroupAsksForTheMinimumOfItsOwnKindOnly() {
        TenantContext.runAs(t.tenantId(), () -> {
            for (String values : List.of("'ALL_OF', 1, NULL", "'ALL_OF', NULL, 6", "'CHOOSE_COUNT', NULL, 6", "'CHOOSE_CREDITS', 2, NULL",
                    "'CHOOSE_COUNT', 0, NULL", "'CHOOSE_CREDITS', NULL, 0", "'CHOOSE_ANY', NULL, NULL")) {
                assertThatThrownBy(() -> jdbc.sql("INSERT INTO pathway_group (tenant_id, program_id, catalog_year, label, kind, min_count, min_credits) "
                                + "SELECT tenant_id, program_id, '2026-27', 'x', " + values + " FROM pathway_group WHERE id = :g")
                        .param("g", groupId).update()).as(values).hasMessageContaining("violates check constraint");
            }
            return null;
        });
    }

    @Test
    void creditsMustBePositiveAndInAKnownUnit() {
        TenantContext.runAs(t.tenantId(), () -> {
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO course_version (tenant_id, course_id, catalog_year, required, credits) "
                    + "SELECT tenant_id, course_id, '2030-31', true, 0 FROM course_version LIMIT 1").update())
                    .hasMessageContaining("violates check constraint");
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO course_version (tenant_id, course_id, catalog_year, required, credit_unit) "
                    + "SELECT tenant_id, course_id, '2030-31', true, 'TRIMESTER' FROM course_version LIMIT 1").update())
                    .hasMessageContaining("violates check constraint");
            return null;
        });
    }
}
