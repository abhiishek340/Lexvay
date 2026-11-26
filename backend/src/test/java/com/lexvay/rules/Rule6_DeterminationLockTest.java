package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.common.TenantContext;
import com.lexvay.determination.VersionsHash;
import com.lexvay.review.ReviewFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 6: a signed determination is locked and names the exact versions it used. */
class Rule6_DeterminationLockTest extends AbstractAssessmentTest {

    @Autowired JdbcClient jdbc;

    @Test
    void storedVersionsReproduceTheHashAndNothingButStatusCanChange() throws Exception {
        var rf = new ReviewFixtures(mvc, json, fx);
        var s = rf.californiaScenario(t, "lock");
        rf.decideAll(t, s);
        UUID id = UUID.fromString(fx.read(rf.sign(t.signer(), s.programId(), "CA", "MEETS")).get("id").asString());

        Map<String, Object> row = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT rule_version_id, versions_hash FROM determination WHERE id = :id").param("id", id).query().singleRow());
        List<UUID> courseVersions = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT course_version_id FROM determination_course_version WHERE determination_id = :id").param("id", id).query(UUID.class).list());
        assertThat(courseVersions).hasSize(2);
        assertThat(VersionsHash.compute((UUID) row.get("rule_version_id"), courseVersions)).isEqualTo(row.get("versions_hash"));

        assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "UPDATE determination SET outcome = 'DOES_NOT_MEET' WHERE id = :id").param("id", id).update()))
                .hasMessageContaining("only status may change");
        assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "UPDATE determination SET versions_hash = :h WHERE id = :id").param("h", "0".repeat(64)).param("id", id).update()))
                .hasMessageContaining("only status may change");
        assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "DELETE FROM determination_course_version WHERE determination_id = :id").param("id", id).update()))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "DELETE FROM determination WHERE id = :id").param("id", id).update()))
                .hasMessageContaining("immutable");
        int reopened = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "UPDATE determination SET status = 'REOPENED' WHERE id = :id").param("id", id).update());
        assertThat(reopened).isEqualTo(1);
    }
}
