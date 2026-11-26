package com.lexvay.review;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.auth.LexvayUser;
import com.lexvay.common.TestTenants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** A finished assessment for California with three mappings: one found, one missing, one ambiguous. */
public class ReviewFixtures {

    public record Scenario(UUID programId, UUID assessmentId, UUID foundMapping, UUID missingMapping, UUID ambiguousMapping,
                           UUID ruleVersionId, UUID course605Version, UUID course650Version) {
    }

    private final MockMvcTester mvc;
    private final ObjectMapper json;
    private final AssessmentFixtures fx;

    public ReviewFixtures(MockMvcTester mvc, ObjectMapper json, AssessmentFixtures fx) {
        this.mvc = mvc;
        this.json = json;
        this.fx = fx;
    }

    public Scenario californiaScenario(TestTenants.TenantWithUsers t, String labelSuffix) {
        UUID programId = fx.program(t, List.of("CA", "UT"));
        UUID cv605 = fx.course(t, programId, "COUN 605", true);
        UUID cv650 = fx.course(t, programId, "COUN 650", false);
        UUID syllabus = fx.document(t, programId, "coun605.txt", "SYLLABUS", cv605, com.lexvay.assessment.AbstractAssessmentTest.SYLLABUS_605);
        UUID plan = fx.document(t, programId, "degree-plan.txt", "DEGREE_PLAN", null, com.lexvay.assessment.AbstractAssessmentTest.DEGREE_PLAN);
        assertThat(fx.fact(t, programId, "TOTAL_CREDITS", "60", plan, 1, "a total of 60 semester credit hours")).hasStatus(201);
        UUID ruleVersionId = fx.approvedRuleVersion(t, "CA", "Demo rule set California " + labelSuffix, "Fictional CA rule.", fx.requirements("CA", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "at least 60 semester hours"),
                AssessmentFixtures.specific("Telehealth", "training in telehealth practice"),
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"))));
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah " + labelSuffix, "Fictional UT rule.", fx.requirements("UT", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "at least 60 semester hours"))));
        fx.fakeScript("MATCH:CA-2", fx.matchOutput("EVIDENCE_MISSING", null, null, null, "Nothing mentions telehealth."));
        fx.fakeScript("MATCH:CA-3", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1, "ACA Code of Ethics", "Names the code only."));
        UUID assessmentId = fx.runAssessment(t, programId);
        JsonNode mappings = fx.mappings(t, assessmentId, "CA");
        return new Scenario(programId, assessmentId,
                UUID.fromString(fx.mappingFor(mappings, "CA-1").get("id").asString()),
                UUID.fromString(fx.mappingFor(mappings, "CA-2").get("id").asString()),
                UUID.fromString(fx.mappingFor(mappings, "CA-3").get("id").asString()),
                ruleVersionId, cv605, cv650);
    }

    public MvcTestResult review(LexvayUser as, UUID mappingId, String kind, String note) {
        Map<String, Object> body = new HashMap<>();
        body.put("kind", kind);
        body.put("note", note);
        return mvc.post().uri("/api/v1/mappings/" + mappingId + "/reviews").with(user(as)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
    }

    public MvcTestResult sign(LexvayUser as, UUID programId, String jurisdiction, String outcome) {
        return mvc.post().uri("/api/v1/programs/" + programId + "/determinations").with(user(as)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jurisdiction", jurisdiction, "outcome", outcome))).exchange();
    }

    public void decideAll(TestTenants.TenantWithUsers t, Scenario s) {
        assertThat(review(t.reviewer(), s.foundMapping(), "ACCEPTED", null)).hasStatus(201);
        assertThat(review(t.reviewer(), s.missingMapping(), "GAP_CONFIRMED", "No telehealth content anywhere in the curriculum.")).hasStatus(201);
        assertThat(review(t.reviewer(), s.ambiguousMapping(), "EVIDENCE_REQUESTED", "Please send the week-by-week ethics schedule.")).hasStatus(201);
    }
}
