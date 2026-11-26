package com.lexvay.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class ReviewApiTest extends AbstractAssessmentTest {

    @Autowired AuditRepository audit;
    @Autowired JdbcClient jdbc;

    ReviewFixtures rf;
    ReviewFixtures.Scenario s;

    @BeforeEach
    void scenario() {
        rf = new ReviewFixtures(mvc, json, fx);
        s = rf.californiaScenario(t, "review " + UUID.randomUUID().toString().substring(0, 6));
    }

    @Test
    void decisionsAreRecordedAndTheLatestWins() throws Exception {
        var accepted = rf.review(t.reviewer(), s.foundMapping(), "ACCEPTED", null);
        assertThat(accepted).hasStatus(201);
        var decision = fx.read(accepted).get("decision");
        assertThat(decision.get("kind").asString()).isEqualTo("ACCEPTED");
        assertThat(decision.get("userId").asString()).isEqualTo(t.reviewer().id().toString());
        assertThat(decision.get("userDisplayName").asString()).isEqualTo(t.reviewer().displayName());

        var gap = rf.review(t.signer(), s.foundMapping(), "GAP_CONFIRMED", "On second look the credits are quarter hours.");
        assertThat(gap).hasStatus(201);
        var latest = fx.read(gap).get("decision");
        assertThat(latest.get("kind").asString()).isEqualTo("GAP_CONFIRMED");
        assertThat(latest.get("note").asString()).contains("quarter hours");

        var mapping = fx.mappingFor(fx.mappings(t, s.assessmentId(), "CA"), "CA-1");
        assertThat(mapping.get("decision").get("kind").asString()).isEqualTo("GAP_CONFIRMED");
        assertThat(fx.mappingFor(fx.mappings(t, s.assessmentId(), "CA"), "CA-2").get("decision").isNull()).isTrue();

        var summary = fx.assessment(t, s.assessmentId()).get("jurisdictions");
        for (var j : summary) {
            if (j.get("jurisdiction").asString().equals("CA")) {
                assertThat(j.get("decided").asInt()).isEqualTo(1);
                assertThat(j.get("total").asInt()).isEqualTo(3);
            }
        }

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(s.foundMapping()));
        assertThat(entries).extracting(e -> e.action()).containsExactly("REVIEW_RECORDED", "REVIEW_RECORDED");
        assertThat(entries.get(0).detail().get("kind")).isEqualTo("GAP_CONFIRMED");
        assertThat(entries.get(0).userId()).isEqualTo(t.signer().id());
    }

    @Test
    void confirmingAGapRequiresANoteAtTheApiAndInTheDatabase() throws Exception {
        var noNote = rf.review(t.reviewer(), s.missingMapping(), "GAP_CONFIRMED", "   ");
        assertThat(noNote).hasStatus(400);
        assertThat(fx.read(noNote).get("detail").asString()).contains("requires a note");
        assertThat(rf.review(t.reviewer(), s.missingMapping(), "GAP_CONFIRMED", null)).hasStatus(400);
        assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                        "INSERT INTO review_event (tenant_id, mapping_id, kind, note, user_id) VALUES (:t, :m, 'GAP_CONFIRMED', '', :u)")
                .param("t", t.tenantId()).param("m", s.missingMapping()).param("u", t.reviewer().id()).update()))
                .hasMessageContaining("gap_confirmed_requires_note");
        assertThat(fx.mappingFor(fx.mappings(t, s.assessmentId(), "CA"), "CA-2").get("decision").isNull()).isTrue();
    }

    @Test
    void reopenedIsSystemOnlyAndRemovesTheDecision() throws Exception {
        var user = rf.review(t.reviewer(), s.ambiguousMapping(), "REOPENED", "trying");
        assertThat(user).hasStatus(400);
        assertThat(fx.read(user).get("detail").asString()).contains("recorded by the system");

        assertThat(rf.review(t.reviewer(), s.ambiguousMapping(), "ACCEPTED", null)).hasStatus(201);
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                        "INSERT INTO review_event (tenant_id, mapping_id, kind, note, user_id, created_at) VALUES (:t, :m, 'REOPENED', 'rule changed', NULL, clock_timestamp())")
                .param("t", t.tenantId()).param("m", s.ambiguousMapping()).update());
        assertThat(fx.mappingFor(fx.mappings(t, s.assessmentId(), "CA"), "CA-3").get("decision").isNull()).as("D-003").isTrue();

        assertThat(rf.review(t.reviewer(), s.ambiguousMapping(), "ACCEPTED", "re-reviewed")).hasStatus(201);
        assertThat(fx.mappingFor(fx.mappings(t, s.assessmentId(), "CA"), "CA-3").get("decision").get("kind").asString()).isEqualTo("ACCEPTED");
    }

    @Test
    void staleMappingsRolesAndTenancy() throws Exception {
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("UPDATE mapping SET stale = true WHERE id = :id").param("id", s.foundMapping()).update());
        var stale = rf.review(t.reviewer(), s.foundMapping(), "ACCEPTED", null);
        assertThat(stale).hasStatus(409);
        assertThat(fx.read(stale).get("detail").asString()).contains("outdated");

        assertThat(rf.review(t.contributor(), s.missingMapping(), "ACCEPTED", null)).hasStatus(403);
        var other = tenants.create("other");
        assertThat(rf.review(other.admin(), s.missingMapping(), "ACCEPTED", null)).hasStatus(404);
        assertThat(rf.review(t.reviewer(), UUID.randomUUID(), "ACCEPTED", null)).hasStatus(404);
    }
}
