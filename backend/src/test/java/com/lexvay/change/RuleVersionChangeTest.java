package com.lexvay.change;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import com.lexvay.review.ReviewFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

class RuleVersionChangeTest extends AbstractAssessmentTest {

    @Autowired AuditRepository audit;

    private MvcTestResult createVersion(String label, String jurisdiction, UUID supersedes, String requirementsJson) {
        fake.script("EXTRACT:" + label, requirementsJson);
        var map = new java.util.HashMap<String, Object>(Map.of("jurisdiction", jurisdiction, "licenseType", AssessmentFixtures.LICENSE, "label", label,
                "sourceUrl", "https://example.invalid/" + label.replace(' ', '-'), "effectiveFrom", LocalDate.of(2027, 1, 1).toString(),
                "text", "Fictional revised rule text for " + label));
        if (supersedes != null) {
            map.put("supersedesId", supersedes.toString());
        }
        return mvc.post().uri("/api/v1/rule-versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(map)).exchange();
    }

    @Test
    void aTermsOnlyEditCarriesTheDecisionsOverAndTheDiffReportsIt() throws Exception {
        var rf = new ReviewFixtures(mvc, json, fx);
        var s = rf.californiaScenario(t, "terms");
        rf.decideAll(t, s);

        // The same three requirements; a reviewer or the model gave ethics search terms in the revision.
        String revised = fx.requirements("CA", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "at least 60 semester hours"),
                AssessmentFixtures.specific("Telehealth", "training in telehealth practice"),
                AssessmentFixtures.withTerms(AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"),
                        "code of ethics", "confidentiality")));
        UUID newVersion = UUID.fromString(fx.read(createVersion("Demo rule set California terms rev 2", "CA", s.ruleVersionId(), revised))
                .get("ruleVersionId").asString());
        runner.runPending();

        var diff = fx.read(mvc.get().uri("/api/v1/rule-versions/" + newVersion + "/diff").with(user(t.reviewer())).exchange());
        assertThat(diff.get("summary").get("changed").asInt()).isEqualTo(1);
        assertThat(diff.get("summary").get("unchanged").asInt()).isEqualTo(2);
        JsonNode ethicsRow = diff.get("requirements").get(2);
        assertThat(ethicsRow.get("status").asString()).isEqualTo("CHANGED");
        assertThat(ethicsRow.get("changes")).extracting(JsonNode::asString).containsExactly("SEARCH_TERMS");
        assertThat(ethicsRow.get("before").get("searchTerms")).isEmpty();
        assertThat(ethicsRow.get("after").get("searchTerms")).extracting(JsonNode::asString).containsExactly("code of ethics", "confidentiality");

        var eventId = UUID.fromString(fx.read(mvc.get().uri("/api/v1/change-events").with(user(t.reviewer())).exchange()).get(0).get("id").asString());
        assertThat(mvc.post().uri("/api/v1/rule-versions/" + newVersion + "/requirements/approve").with(user(t.reviewer())).with(csrf()).exchange()).hasStatus(200);
        assertThat(mvc.post().uri("/api/v1/change-events/" + eventId + "/rerun").with(user(t.reviewer())).with(csrf()).exchange()).hasStatus(202);
        runner.runPending();

        var newList = fx.mappings(t, s.assessmentId(), "CA");
        assertThat(newList).allSatisfy(m -> assertThat(m.get("ruleVersionId").asString()).isEqualTo(newVersion.toString()));
        assertThat(newList).as("every decision carried over: a change of terms is not a change of the requirement")
                .allSatisfy(m -> assertThat(m.get("decision").get("note").asString()).startsWith("Carried over from"));
        assertThat(fake.callCount(com.lexvay.llm.LlmPurpose.MATCH_EVIDENCE)).as("no new model call").isEqualTo(2);
    }

    @Test
    void rerunCarriesOverUnchangedAndReevaluatesChanged() throws Exception {
        var rf = new ReviewFixtures(mvc, json, fx);
        var s = rf.californiaScenario(t, "change");
        rf.decideAll(t, s);
        UUID signed = UUID.fromString(fx.read(rf.sign(t.signer(), s.programId(), "CA", "MEETS")).get("id").asString());

        // A revision raises the credit hours (changed), keeps telehealth and ethics (unchanged).
        String revised = fx.requirements("CA", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 66, "at least 66 semester hours"),
                AssessmentFixtures.specific("Telehealth", "training in telehealth practice"),
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards")));
        var created = createVersion("Demo rule set California rev 2", "CA", s.ruleVersionId(), revised);
        assertThat(created).hasStatus(202);
        UUID newVersion = UUID.fromString(fx.read(created).get("ruleVersionId").asString());

        var events = fx.read(mvc.get().uri("/api/v1/change-events").with(user(t.reviewer())).exchange());
        assertThat(events).hasSize(1);
        JsonNode event = events.get(0);
        assertThat(event.get("kind").asString()).isEqualTo("RULE_VERSION");
        assertThat(event.get("status").asString()).isEqualTo("OPEN");
        assertThat(event.get("jurisdiction").asString()).isEqualTo("CA");
        assertThat(event.get("newApproved").asBoolean()).isFalse();
        assertThat(event.get("reopenedDeterminationIds").get(0).asString()).isEqualTo(signed.toString());
        UUID eventId = UUID.fromString(event.get("id").asString());

        var determinations = fx.read(mvc.get().uri("/api/v1/programs/" + s.programId() + "/determinations").with(user(t.reviewer())).exchange());
        assertThat(determinations.get(0).get("status").asString()).isEqualTo("REOPENED");
        assertThat(TenantContext.runAs(t.tenantId(), () -> audit.forEntity(signed))).extracting(e -> e.action()).contains("DETERMINATION_REOPENED");

        var oldList = fx.mappings(t, s.assessmentId(), "CA");
        assertThat(oldList).as("old list stays visible until the new version is approved and re-run").hasSize(3);
        assertThat(oldList).allSatisfy(m -> assertThat(m.get("stale").asBoolean()).isTrue());
        assertThat(rf.review(t.reviewer(), s.foundMapping(), "ACCEPTED", null)).as("stale mappings cannot be reviewed").hasStatus(409);

        var tooEarly = mvc.post().uri("/api/v1/change-events/" + eventId + "/rerun").with(user(t.reviewer())).with(csrf()).exchange();
        assertThat(tooEarly).hasStatus(409);
        assertThat(fx.read(tooEarly).get("detail").asString()).contains("Approve");

        runner.runPending();
        assertThat(mvc.post().uri("/api/v1/rule-versions/" + newVersion + "/requirements/approve").with(user(t.reviewer())).with(csrf()).exchange()).hasStatus(200);
        var rerun = mvc.post().uri("/api/v1/change-events/" + eventId + "/rerun").with(user(t.reviewer())).with(csrf()).exchange();
        assertThat(rerun).hasStatus(202);
        runner.runPending();

        var newList = fx.mappings(t, s.assessmentId(), "CA");
        assertThat(newList).hasSize(3);
        assertThat(newList).allSatisfy(m -> assertThat(m.get("ruleVersionId").asString()).isEqualTo(newVersion.toString()));
        var credits = fx.mappingFor(newList, "CA-1");
        assertThat(credits.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(credits.get("rationale").asString()).isEqualTo("TOTAL_CREDITS 60 >= 66 → false");
        assertThat(credits.get("decision").isNull()).as("changed requirement starts undecided").isTrue();
        var telehealth = fx.mappingFor(newList, "CA-2");
        assertThat(telehealth.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(telehealth.get("decision").get("kind").asString()).isEqualTo("GAP_CONFIRMED");
        assertThat(telehealth.get("decision").get("note").asString()).startsWith("Carried over from");
        assertThat(telehealth.get("decision").get("userId").asString()).isEqualTo(t.reviewer().id().toString());
        var ethics = fx.mappingFor(newList, "CA-3");
        assertThat(ethics.get("decision").get("kind").asString()).isEqualTo("EVIDENCE_REQUESTED");
        assertThat(ethics.get("evidence").get("quote").asString()).isEqualTo("ACA Code of Ethics");
        assertThat(fake.callCount(com.lexvay.llm.LlmPurpose.MATCH_EVIDENCE)).as("only the two original matches; carried-over ones need no model call").isEqualTo(2);

        var handled = fx.read(mvc.get().uri("/api/v1/change-events").with(user(t.reviewer())).exchange()).get(0);
        assertThat(handled.get("status").asString()).isEqualTo("HANDLED");
        assertThat(mvc.post().uri("/api/v1/change-events/" + eventId + "/rerun").with(user(t.reviewer())).with(csrf()).exchange()).hasStatus(409);

        assertThat(rf.sign(t.signer(), s.programId(), "CA", "DOES_NOT_MEET")).as("the changed requirement is undecided").hasStatus(409);
        assertThat(rf.review(t.reviewer(), UUID.fromString(credits.get("id").asString()), "GAP_CONFIRMED", "60 credits is below the new 66.")).hasStatus(201);
        var resigned = rf.sign(t.signer(), s.programId(), "CA", "DOES_NOT_MEET");
        assertThat(resigned).as("a reopened determination can be replaced by a new signature").hasStatus(201);
        assertThat(fx.read(resigned).get("ruleVersionId").asString()).isEqualTo(newVersion.toString());
    }
}
