package com.lexvay.rule;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import com.lexvay.review.ReviewFixtures;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/**
 * RG-1: a rule version's legal status and dates are recorded by a person with a citation, corrections append, only a
 * final version can be approved, and an assessment uses the rule in force on its date (D-551 to D-556).
 */
class LegalTimeApiTest extends AbstractAssessmentTest {

    private static final String BASIS = "Fictional Bulletin notice, section 3";

    @Autowired JdbcClient jdbc;
    @Autowired RuleVersionRepository ruleVersions;
    @Autowired JurisdictionRepository jurisdictions;
    @Autowired AuditRepository audit;

    private String requirements(String jurisdiction) {
        return fx.requirements(jurisdiction, List.of(AssessmentFixtures.subject("Ethics", "coursework in professional ethics")));
    }

    /** Creates a rule version through the API with the given legal fields, extracts its requirements, and returns its id. */
    private UUID draft(String jurisdiction, String label, Map<String, Object> legal) throws Exception {
        fake.script("EXTRACT:" + label, requirements(jurisdiction));
        var body = new HashMap<String, Object>(Map.of("jurisdiction", jurisdiction, "licenseType", AssessmentFixtures.LICENSE,
                "label", label, "sourceUrl", "https://example.invalid/" + label.replace(' ', '-'), "text", "Fictional rule text. " + label));
        body.putAll(legal);
        var created = mvc.post().uri("/api/v1/rule-versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
        assertThat(created).hasStatus(202);
        UUID id = UUID.fromString(fx.read(created).get("ruleVersionId").asString());
        runner.runPending();
        return id;
    }

    private static Map<String, Object> legal(String status, String effectiveFrom) {
        return Map.of("legalStatus", status, "effectiveFrom", effectiveFrom, "datesBasis", BASIS);
    }

    private MvcTestResult recordStatus(UUID id, Map<String, Object> body) throws Exception {
        return mvc.post().uri("/api/v1/rule-versions/" + id + "/status").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
    }

    private MvcTestResult approve(UUID id) {
        return mvc.post().uri("/api/v1/rule-versions/" + id + "/requirements/approve").with(user(t.reviewer())).with(csrf()).exchange();
    }

    private JsonNode view(UUID id) {
        return fx.read(mvc.get().uri("/api/v1/rule-versions/" + id).with(user(t.reviewer())).exchange());
    }

    private UUID jurisdictionId(String code) {
        return jurisdictions.findByCodes(List.of(code)).get(0).id();
    }

    private Optional<RuleVersion> inForce(String jurisdiction, LocalDate asOf) {
        UUID id = jurisdictionId(jurisdiction);
        return Optional.ofNullable(TenantContext.runAs(t.tenantId(), () -> ruleVersions.findInForce(List.of(id),
                AssessmentFixtures.LICENSE, asOf)).get(id));
    }

    private List<Map<String, Object>> statusRows(UUID id) {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT legal_status, effective_from, effective_to, dates_basis, recorded_by "
                + "FROM rule_version_status WHERE rule_version_id = :id ORDER BY recorded_at, id").param("id", id).query().listOfRows());
    }

    // --- approval needs a recorded, final status and dates ---

    @Test
    void aVersionWithNoStatusOrDatesIsUnknownAndCannotBeApproved() throws Exception {
        UUID id = draft("CA", "Rule set without a status", Map.of());

        var view = view(id);
        assertThat(view.get("legalStatus").asString()).isEqualTo("UNKNOWN");
        assertThat(view.get("effectiveFrom").isNull()).isTrue();
        assertThat(view.get("datesBasis").isNull()).isTrue();
        var refused = approve(id);
        assertThat(refused).hasStatus(409);
        assertThat(fx.read(refused).get("detail").asString()).contains("legal status is UNKNOWN");
        assertThat(view(id).get("approval").isNull()).isTrue();
    }

    @Test
    void aProposedVersionIsRefusedEvenWithDates() throws Exception {
        UUID id = draft("CA", "Proposed rule set", legal("PROPOSED", "2027-03-01"));

        var refused = approve(id);

        assertThat(refused).hasStatus(409);
        assertThat(fx.read(refused).get("detail").asString()).contains("PROPOSED");
        assertThat(statusRows(id)).isEmpty();
    }

    @Test
    void withdrawnAndRepealedVersionsAreRefused() throws Exception {
        UUID withdrawn = draft("CA", "Withdrawn rule set", legal("WITHDRAWN", "2026-07-01"));
        UUID repealed = draft("UT", "Repealed rule set", legal("REPEALED", "2026-07-01"));

        assertThat(approve(withdrawn)).hasStatus(409);
        assertThat(approve(repealed)).hasStatus(409);
    }

    @Test
    void aFinalVersionWithoutAnEffectiveDateIsRefused() throws Exception {
        UUID id = draft("CA", "Final but undated", Map.of("legalStatus", "IN_FORCE"));

        var refused = approve(id);

        assertThat(refused).hasStatus(409);
        assertThat(fx.read(refused).get("detail").asString()).contains("effective date is not recorded");
    }

    @Test
    void aDateNeedsItsBasisAndAnEndNeedsToFollowTheStart() throws Exception {
        UUID id = draft("CA", "Rule set to correct", Map.of());

        var noBasis = recordStatus(id, Map.of("legalStatus", "IN_FORCE", "effectiveFrom", "2026-07-01"));
        assertThat(noBasis).hasStatus(400);
        assertThat(fx.read(noBasis).get("detail").asString()).contains("needs its basis");
        var blankBasis = recordStatus(id, Map.of("legalStatus", "IN_FORCE", "effectiveFrom", "2026-07-01", "datesBasis", "   "));
        assertThat(blankBasis).hasStatus(400);
        var endsBeforeItStarts = recordStatus(id, Map.of("legalStatus", "IN_FORCE", "effectiveFrom", "2026-07-01",
                "effectiveTo", "2026-07-01", "datesBasis", BASIS));
        assertThat(endsBeforeItStarts).hasStatus(400);
        assertThat(recordStatus(id, Map.of("legalStatus", "NOT_A_STATUS"))).hasStatus(400);
        assertThat(statusRows(id)).isEmpty();

        var onCreate = mvc.post().uri("/api/v1/rule-versions").with(user(t.contributor())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jurisdiction", "CA", "licenseType", AssessmentFixtures.LICENSE, "label", "No basis",
                        "sourceUrl", "https://example.invalid/x", "effectiveFrom", "2026-07-01", "text", "Fictional rule text")))
                .exchange();
        assertThat(onCreate).hasStatus(400);
    }

    @Test
    void aReviewerRecordsTheStatusThenApprovesAndTheApprovalPinsItAsAStatusRow() throws Exception {
        UUID id = draft("CA", "Rule set recorded by a person", Map.of());

        var recorded = recordStatus(id, Map.of("legalStatus", "IN_FORCE", "effectiveFrom", "2026-07-01", "publishedAt", "2026-06-01",
                "adoptedAt", "2026-05-15", "datesBasis", BASIS));
        assertThat(recorded).hasStatus(200);
        assertThat(fx.read(recorded).get("legalStatus").asString()).isEqualTo("IN_FORCE");
        assertThat(fx.read(recorded).get("effectiveFrom").asString()).isEqualTo("2026-07-01");
        assertThat(fx.read(recorded).get("publishedAt").asString()).isEqualTo("2026-06-01");
        assertThat(fx.read(recorded).get("adoptedAt").asString()).isEqualTo("2026-05-15");
        assertThat(fx.read(recorded).get("datesBasis").asString()).isEqualTo(BASIS);
        assertThat(approve(id)).hasStatus(200);

        var rows = statusRows(id);
        assertThat(rows).extracting(r -> r.get("legal_status")).containsExactly("IN_FORCE", "IN_FORCE");
        assertThat(rows.get(1).get("recorded_by")).as("the approval is signed by the approver").isEqualTo(t.reviewer().id());
        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(id));
        var statusEntry = entries.stream().filter(e -> e.action().equals("RULE_STATUS_RECORDED")).findFirst().orElseThrow();
        assertThat(statusEntry.userId()).isEqualTo(t.reviewer().id());
        assertThat(statusEntry.detail()).containsEntry("legalStatus", "IN_FORCE").containsEntry("effectiveFrom", "2026-07-01")
                .containsEntry("previousLegalStatus", "UNKNOWN").containsEntry("datesBasis", BASIS);
        var approval = entries.stream().filter(e -> e.action().equals("REQUIREMENTS_APPROVED")).findFirst().orElseThrow();
        assertThat(approval.detail()).containsEntry("legalStatus", "IN_FORCE").containsEntry("effectiveFrom", "2026-07-01");
    }

    @Test
    void onlyAReviewerOrAnAdministratorRecordsAStatus() throws Exception {
        UUID id = draft("CA", "Rule set for roles", Map.of());
        var body = json.writeValueAsString(Map.of("legalStatus", "WITHDRAWN"));

        for (var as : List.of(t.contributor(), t.signer())) {
            assertThat(mvc.post().uri("/api/v1/rule-versions/" + id + "/status").with(user(as)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body).exchange()).hasStatus(403);
        }
        assertThat(mvc.post().uri("/api/v1/rule-versions/" + id + "/status").with(user(t.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange()).hasStatus(200);
        assertThat(mvc.post().uri("/api/v1/rule-versions/" + UUID.randomUUID() + "/status").with(user(t.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange()).hasStatus(404);
    }

    // --- corrections append ---

    @Test
    void correctionsAppendTheNewestIsCurrentAndNoRowIsEverChanged() throws Exception {
        UUID id = draft("CA", "Rule set corrected twice", legal("IN_FORCE", "2026-07-01"));
        assertThat(approve(id)).hasStatus(200);
        var first = statusRows(id);

        assertThat(recordStatus(id, Map.of("legalStatus", "SUPERSEDED", "effectiveFrom", "2026-07-01", "effectiveTo", "2026-12-01",
                "datesBasis", "Replaced by a later notice"))).hasStatus(200);
        assertThat(recordStatus(id, Map.of("legalStatus", "IN_FORCE", "effectiveFrom", "2026-07-02", "datesBasis", "Notice corrected"))).hasStatus(200);

        var rows = statusRows(id);
        assertThat(rows).extracting(r -> r.get("legal_status")).containsExactly("IN_FORCE", "SUPERSEDED", "IN_FORCE");
        assertThat(rows.get(0)).as("the earlier row is as it was").isEqualTo(first.get(0));
        var current = view(id);
        assertThat(current.get("legalStatus").asString()).isEqualTo("IN_FORCE");
        assertThat(current.get("effectiveFrom").asString()).isEqualTo("2026-07-02");
        assertThat(current.get("effectiveTo").isNull()).isTrue();
        assertThat(current.get("datesBasis").asString()).isEqualTo("Notice corrected");
        var creation = TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT legal_status, effective_from, dates_basis FROM rule_version WHERE id = :id")
                .param("id", id).query().singleRow());
        assertThat(creation).as("the version row keeps its creation values").containsEntry("legal_status", "IN_FORCE")
                .containsEntry("dates_basis", BASIS);
        assertThat(creation.get("effective_from").toString()).isEqualTo("2026-07-01");
    }

    @Test
    void theVersionRowsDatesAndStatusCannotBeEditedInPlace() throws Exception {
        UUID id = draft("CA", "Rule set locked columns", legal("IN_FORCE", "2026-07-01"));

        for (String assignment : List.of("legal_status = 'REPEALED'", "effective_from = DATE '2020-01-01'", "effective_to = DATE '2030-01-01'",
                "published_at = DATE '2020-01-01'", "adopted_at = DATE '2020-01-01'", "dates_basis = 'edited'", "observed_at = now()")) {
            assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () -> jdbc.sql("UPDATE rule_version SET " + assignment + " WHERE id = :id")
                    .param("id", id).update())).as(assignment).hasMessageContaining("only the approval columns may change");
        }
    }

    // --- selection by the assessment date ---

    @Test
    void theRuleInForceIsSelectedByDateAcrossSupersededFutureAndExpiredVersions() throws Exception {
        UUID old = draft("CA", "Rule set old", legal("IN_FORCE", "2026-01-01"));
        UUID current = draft("CA", "Rule set current", legal("IN_FORCE", "2026-07-01"));
        UUID next = draft("CA", "Rule set next", legal("FINAL_NOT_EFFECTIVE", "2027-01-01"));
        UUID replaced = draft("CA", "Rule set replaced", legal("IN_FORCE", "2026-04-01"));
        for (UUID id : List.of(old, current, next, replaced)) {
            assertThat(approve(id)).hasStatus(200);
        }
        assertThat(recordStatus(old, Map.of("legalStatus", "IN_FORCE", "effectiveFrom", "2026-01-01", "effectiveTo", "2026-07-01",
                "datesBasis", "Ended when the next took effect"))).hasStatus(200);
        assertThat(recordStatus(replaced, Map.of("legalStatus", "SUPERSEDED", "effectiveFrom", "2026-04-01", "datesBasis", BASIS))).hasStatus(200);

        assertThat(inForce("CA", LocalDate.of(2025, 12, 31))).as("before any version").isEmpty();
        assertThat(inForce("CA", LocalDate.of(2026, 3, 1))).map(RuleVersion::id).as("only the old one has begun").contains(old);
        assertThat(inForce("CA", LocalDate.of(2026, 5, 1))).map(RuleVersion::id).as("superseded is never selected").contains(old);
        assertThat(inForce("CA", LocalDate.of(2026, 6, 30))).map(RuleVersion::id).contains(old);
        assertThat(inForce("CA", LocalDate.of(2026, 7, 1))).map(RuleVersion::id).as("the end date is exclusive").contains(current);
        assertThat(inForce("CA", LocalDate.of(2026, 12, 31))).map(RuleVersion::id).as("final but not effective yet").contains(current);
        assertThat(inForce("CA", LocalDate.of(2027, 1, 1))).map(RuleVersion::id).as("final, and effective now").contains(next);
        assertThat(TenantContext.runAs(t.tenantId(), () -> ruleVersions.findUpcoming(List.of(jurisdictionId("CA")), AssessmentFixtures.LICENSE,
                LocalDate.of(2026, 12, 31))).get(jurisdictionId("CA"))).extracting(RuleVersion::id).isEqualTo(next);
        assertThat(TenantContext.runAs(t.tenantId(), () -> ruleVersions.findUpcoming(List.of(jurisdictionId("CA")), AssessmentFixtures.LICENSE,
                LocalDate.of(2027, 1, 1)))).isEmpty();
    }

    @Test
    void anExpiredRuleIsNotInForceAndTheNewestVersionIsNeverUsedInItsPlace() throws Exception {
        UUID ended = draft("TX", "Texas rule set ended", Map.of("legalStatus", "IN_FORCE", "effectiveFrom", "2026-01-01",
                "effectiveTo", "2026-06-01", "datesBasis", BASIS));
        assertThat(approve(ended)).hasStatus(200);

        assertThat(inForce("TX", LocalDate.of(2026, 5, 31))).map(RuleVersion::id).contains(ended);
        assertThat(inForce("TX", LocalDate.of(2026, 6, 1))).as("the only version has ended").isEmpty();
        assertThat(inForce("TX", LocalDate.of(2030, 1, 1))).isEmpty();
    }

    @Test
    void anUnapprovedVersionIsNeverInForce() throws Exception {
        draft("AZ", "Arizona rule set not approved", legal("IN_FORCE", "2026-01-01"));

        assertThat(inForce("AZ", LocalDate.of(2026, 9, 1))).isEmpty();
    }

    @Test
    void withNoRuleInForceOnTheDateTheAssessmentDoesNotRun() throws Exception {
        UUID programId = fx.program(t, List.of("CA", "UT"));
        UUID ca = draft("CA", "California rule set", legal("IN_FORCE", "2026-01-01"));
        assertThat(approve(ca)).hasStatus(200);
        UUID ut = draft("UT", "Utah rule set not yet", legal("FINAL_NOT_EFFECTIVE", "2027-06-01"));
        assertThat(approve(ut)).hasStatus(200);
        long before = TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT count(*) FROM assessment").query(Long.class).single());

        var refused = mvc.post().uri("/api/v1/programs/" + programId + "/assessments").with(user(t.reviewer())).with(csrf()).exchange();

        assertThat(refused).hasStatus(409);
        assertThat(fx.read(refused).get("detail").asString()).contains("No rule in force on " + LocalDate.now(java.time.ZoneOffset.UTC))
                .contains("in: UT").doesNotContain("CA,");
        assertThat(TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT count(*) FROM assessment").query(Long.class).single()))
                .as("nothing was started").isEqualTo(before);
    }

    @Test
    void theAssessmentDateIsSettableAndTheRunUsesTheRuleInForceOnIt() throws Exception {
        UUID programId = fx.program(t, List.of("CA"));
        UUID early = draft("CA", "California early", legal("IN_FORCE", "2026-01-01"));
        assertThat(approve(early)).hasStatus(200);
        assertThat(recordStatus(early, Map.of("legalStatus", "IN_FORCE", "effectiveFrom", "2026-01-01", "effectiveTo", "2026-10-01",
                "datesBasis", "Ended when the later rule took effect"))).hasStatus(200);
        UUID late = draft("CA", "California late", legal("IN_FORCE", "2026-10-01"));
        assertThat(approve(late)).hasStatus(200);

        var started = mvc.post().uri("/api/v1/programs/" + programId + "/assessments").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"asOf\":\"2026-05-01\"}").exchange();
        assertThat(started).hasStatus(202);
        UUID assessmentId = UUID.fromString(fx.read(started).get("assessmentId").asString());
        runner.runPending();

        var assessment = fx.assessment(t, assessmentId);
        assertThat(assessment.get("status").asString()).isEqualTo("FINISHED");
        assertThat(assessment.get("assessmentAsOf").asString()).isEqualTo("2026-05-01");
        var used = fx.mappings(t, assessmentId, "CA");
        assertThat(used).hasSize(1);
        UUID usedVersion = TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT r.rule_version_id FROM requirement r "
                + "WHERE r.id = :id").param("id", UUID.fromString(used.get(0).get("requirement").get("id").asString())).query(UUID.class).single());
        assertThat(usedVersion).isEqualTo(early);

        UUID today = fx.runAssessment(t, programId);
        assertThat(fx.assessment(t, today).get("assessmentAsOf").asString()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC).toString());
        assertThat(mvc.post().uri("/api/v1/programs/" + programId + "/assessments").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"asOf\":\"2026-13-45\"}").exchange()).hasStatus(400);
        var tooEarly = mvc.post().uri("/api/v1/programs/" + programId + "/assessments").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"asOf\":\"2025-01-01\"}").exchange();
        assertThat(tooEarly).hasStatus(409);
        assertThat(fx.read(tooEarly).get("detail").asString()).contains("No rule in force on 2025-01-01");
    }

    @Test
    void theOverviewSaysWhichRuleIsInForceWhichIsUpcomingAndWhenNoneIs() throws Exception {
        UUID programId = fx.program(t, List.of("CA", "UT", "TX"));
        UUID ca = draft("CA", "California in force", legal("IN_FORCE", "2026-01-01"));
        UUID caNext = draft("CA", "California upcoming", legal("FINAL_NOT_EFFECTIVE", "2099-01-01"));
        UUID ut = draft("UT", "Utah only upcoming", legal("FINAL_NOT_EFFECTIVE", "2099-06-01"));
        for (UUID id : List.of(ca, caNext, ut)) {
            assertThat(approve(id)).hasStatus(200);
        }

        var overview = fx.read(mvc.get().uri("/api/v1/programs/" + programId + "/overview").with(user(t.reviewer())).exchange());

        var rules = new HashMap<String, JsonNode>();
        overview.get("rules").forEach(r -> rules.put(r.get("jurisdiction").asString(), r));
        assertThat(rules).containsOnlyKeys("CA", "UT", "TX");
        assertThat(rules.get("CA").get("inForce").get("id").asString()).isEqualTo(ca.toString());
        assertThat(rules.get("CA").get("noRuleInForce").isNull()).isTrue();
        assertThat(rules.get("CA").get("upcoming").get("id").asString()).isEqualTo(caNext.toString());
        assertThat(rules.get("CA").get("upcomingNotice").asString()).isEqualTo("Upcoming from 2099-01-01");
        assertThat(rules.get("UT").get("inForce").isNull()).isTrue();
        assertThat(rules.get("UT").get("noRuleInForce").asString()).isEqualTo("No rule in force on " + LocalDate.now(java.time.ZoneOffset.UTC));
        assertThat(rules.get("UT").get("upcomingNotice").asString()).isEqualTo("Upcoming from 2099-06-01");
        assertThat(rules.get("TX").get("noRuleInForce").asString()).startsWith("No rule in force on ");
        assertThat(rules.get("TX").get("upcoming").isNull()).isTrue();
    }

    // --- product rule 6 ---

    @Test
    void aSignedDeterminationKeepsItsExactVersionsWhenTheStatusLaterChanges() throws Exception {
        var rf = new ReviewFixtures(mvc, json, fx);
        var scenario = rf.californiaScenario(t, "status-after-signing");
        rf.decideAll(t, scenario);
        UUID determinationId = UUID.fromString(fx.read(rf.sign(t.signer(), scenario.programId(), "CA", "MEETS")).get("id").asString());
        var before = determinationRow(determinationId);
        UUID ruleVersionId = (UUID) before.get("rule_version_id");

        assertThat(recordStatus(ruleVersionId, Map.of("legalStatus", "REPEALED", "effectiveFrom", "2026-01-01", "effectiveTo", "2026-12-01",
                "datesBasis", "Repealed by a later notice"))).hasStatus(200);

        assertThat(determinationRow(determinationId)).as("the signed record is exactly as it was").isEqualTo(before);
        assertThat(view(ruleVersionId).get("legalStatus").asString()).isEqualTo("REPEALED");
        assertThat(inForce("CA", LocalDate.now(java.time.ZoneOffset.UTC))).as("a new assessment no longer uses it").isEmpty();
    }

    private Map<String, Object> determinationRow(UUID id) {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT status, outcome, rule_version_id, versions_hash, signed_by, signed_at "
                + "FROM determination WHERE id = :id").param("id", id).query().singleRow());
    }
}
