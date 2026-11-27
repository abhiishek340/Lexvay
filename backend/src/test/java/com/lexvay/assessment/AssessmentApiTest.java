package com.lexvay.assessment;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobType;
import com.lexvay.llm.LlmPurpose;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AssessmentApiTest extends AbstractAssessmentTest {

    @Autowired AuditRepository audit;
    @Autowired JobRepository jobs;
    @Autowired MappingRepository mappingRepository;

    @Test
    void fullRunProducesMappingsWithEvidence() throws Exception {
        UUID programId = fx.program(t, List.of("UT", "CA"));
        UUID cv605 = fx.course(t, programId, "COUN 605", true);
        UUID syllabus = fx.document(t, programId, "coun605.txt", "SYLLABUS", cv605, SYLLABUS_605);
        UUID plan = fx.document(t, programId, "degree-plan.txt", "DEGREE_PLAN", null, DEGREE_PLAN);
        assertThat(fx.fact(t, programId, "TOTAL_CREDITS", "60", plan, 1, "a total of 60 semester credit hours")).hasStatus(201);
        assertThat(fx.fact(t, programId, "INTERNSHIP_HOURS", "600", plan, 1, "a 600 clock hour internship")).hasStatus(201);

        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah", "Fictional Utah rule text.", fx.requirements("UT", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "at least 60 semester hours"),
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"),
                AssessmentFixtures.numeric("SUPERVISED_HOURS", "INTERNSHIP_HOURS", ">=", 700, "at least 700 internship hours"))));
        fx.approvedRuleVersion(t, "CA", "Demo rule set California", "Fictional California rule text.", fx.requirements("CA", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "at least 60 semester hours"),
                AssessmentFixtures.specific("Telehealth", "training in telehealth practice"),
                AssessmentFixtures.numeric("SUPERVISED_HOURS", "PRACTICUM_HOURS", ">=", 100, "at least 100 practicum hours"))));

        String ethicsQuote = "legal standards, confidentiality, duty to warn,\nand professional identity of clinical mental health counselors.";
        fake.script("MATCH:UT-2", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1, ethicsQuote,
                "The syllabus names legal standards and confidentiality as course content."));
        fake.script("MATCH:CA-2", fx.matchOutput("EVIDENCE_MISSING", null, null, null, "No passage mentions telehealth."));

        UUID assessmentId = fx.runAssessment(t, programId);

        var view = fx.assessment(t, assessmentId);
        assertThat(view.get("status").asString()).isEqualTo("FINISHED");
        assertThat(view.get("llmModel").asString()).isEqualTo("fake-llm");
        assertThat(view.get("promptVersion").asString()).isEqualTo("match-v2");
        assertThat(view.get("jurisdictions")).hasSize(2);
        var ut = view.get("jurisdictions").get(1);
        assertThat(ut.get("jurisdiction").asString()).isEqualTo("UT");
        assertThat(ut.get("total").asInt()).isEqualTo(3);
        assertThat(ut.get("evidenceFound").asInt()).isEqualTo(2);
        assertThat(ut.get("evidenceMissing").asInt()).isEqualTo(1);

        var utMappings = fx.mappings(t, assessmentId, "UT");
        assertThat(utMappings).hasSize(3);
        var credits = fx.mappingFor(utMappings, "UT-1");
        assertThat(credits.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(credits.get("method").asString()).isEqualTo("RULES_ENGINE");
        assertThat(credits.get("rationale").asString()).isEqualTo("TOTAL_CREDITS 60 >= 60 → true");
        assertThat(credits.get("evidence").get("quote").asString()).isEqualTo("a total of 60 semester credit hours");
        assertThat(credits.get("evidence").get("filename").asString()).isEqualTo("degree-plan.txt");

        var ethics = fx.mappingFor(utMappings, "UT-2");
        assertThat(ethics.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(ethics.get("method").asString()).isEqualTo("MATCH");
        var evidence = ethics.get("evidence");
        assertThat(evidence.get("documentId").asString()).isEqualTo(syllabus.toString());
        assertThat(evidence.get("courseCode").asString()).isEqualTo("COUN 605");
        assertThat(evidence.get("courseRequired").asBoolean()).isTrue();
        assertThat(evidence.get("pageNumber").asInt()).isEqualTo(1);
        assertThat(evidence.get("quote").asString()).startsWith("legal standards, confidentiality, duty to warn,");
        assertThat(SYLLABUS_605.substring(evidence.get("charStart").asInt(), evidence.get("charEnd").asInt()))
                .isEqualTo(evidence.get("quote").asString());

        var hours = fx.mappingFor(utMappings, "UT-3");
        assertThat(hours.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(hours.get("rationale").asString()).isEqualTo("INTERNSHIP_HOURS 600 >= 700 → false");

        var caMappings = fx.mappings(t, assessmentId, "CA");
        assertThat(fx.mappingFor(caMappings, "CA-2").get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(fx.mappingFor(caMappings, "CA-2").get("evidence").isNull()).isTrue();
        assertThat(fx.mappingFor(caMappings, "CA-3").get("rationale").asString()).isEqualTo("No program fact recorded for PRACTICUM_HOURS");

        assertThat(fake.callCount(LlmPurpose.MATCH_EVIDENCE)).as("one model call per non-numeric requirement").isEqualTo(2);
        var prompt = fake.calls().stream().filter(c -> c.scriptKey().equals("MATCH:UT-2")).findFirst().orElseThrow().userPrompt();
        assertThat(prompt).contains("REQUIREMENT UT-2").contains("document_id: " + syllabus).contains("COUN 605 (required)");

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(assessmentId));
        assertThat(entries).extracting(e -> e.action()).containsExactlyInAnyOrder("ASSESSMENT_STARTED", "ASSESSMENT_FINISHED");
    }

    @Test
    void rerunningTheJobDoesNotDuplicateMappings() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah rerun", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "60 hours"))));
        UUID assessmentId = fx.runAssessment(t, programId);
        assertThat(fx.mappings(t, assessmentId, "UT")).hasSize(1);
        TenantContext.runAs(t.tenantId(), () -> jobs.enqueue(JobType.RUN_ASSESSMENT, assessmentId, Map.of("assessmentId", assessmentId.toString())));
        runner.runPending();
        assertThat(fx.mappings(t, assessmentId, "UT")).hasSize(1);
        assertThat(fx.assessment(t, assessmentId).get("status").asString()).isEqualTo("FINISHED");
    }

    @Test
    void startIsRefusedWithoutApprovedRuleVersions() throws Exception {
        UUID programId = fx.program(t, List.of("UT", "TX"));
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah only", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "60 hours"))));
        var result = mvc.post().uri("/api/v1/programs/" + programId + "/assessments").with(user(t.reviewer())).with(csrf()).exchange();
        assertThat(result).hasStatus(409);
        assertThat(fx.read(result).get("detail").asString()).contains("TX").doesNotContain("UT");
    }

    @Test
    void noCandidatePassagesIsEvidenceMissingWithoutAModelCall() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah empty", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics"))));
        UUID assessmentId = fx.runAssessment(t, programId);
        var m = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(m.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(m.get("rationale").asString()).contains("No candidate passages");
        assertThat(fake.callCount(LlmPurpose.MATCH_EVIDENCE)).isZero();
    }

    @Test
    void modelNamingADocumentOutsideTheShortlistIsRejected() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        UUID cv = fx.course(t, programId, "COUN 605", true);
        fx.document(t, programId, "coun605.txt", "SYLLABUS", cv, SYLLABUS_605);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah foreign", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"))));
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", UUID.randomUUID(), 1, "legal standards", "made up"));
        UUID assessmentId = fx.runAssessment(t, programId);
        var m = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(m.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(m.get("rationale").asString()).startsWith("Model output rejected").contains("not among the candidate passages");
    }

    @Test
    void assessmentsAreTenantScoped() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah tenancy", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "60 hours"))));
        UUID assessmentId = fx.runAssessment(t, programId);
        var other = tenants.create("other");
        assertThat(mvc.get().uri("/api/v1/assessments/" + assessmentId).with(user(other.admin())).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/assessments/" + assessmentId + "/mappings?jurisdiction=UT").with(user(other.admin())).exchange()).hasStatus(404);
        assertThat(mvc.post().uri("/api/v1/programs/" + programId + "/assessments").with(user(other.admin())).with(csrf()).exchange()).hasStatus(404);
    }
}
