package com.lexvay.assessment;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.lexvay.common.TestTenants;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Builds a program with courses, documents, facts and approved rule versions through the real API. */
public class AssessmentFixtures {

    public static final String LICENSE = "CLINICAL_MENTAL_HEALTH_COUNSELING";

    private final MockMvcTester mvc;
    private final ObjectMapper json;
    private final JobRunner runner;
    private final FakeLlmClient fake;

    public AssessmentFixtures(MockMvcTester mvc, ObjectMapper json, JobRunner runner, FakeLlmClient fake) {
        this.mvc = mvc;
        this.json = json;
        this.runner = runner;
        this.fake = fake;
    }

    public JsonNode read(MvcTestResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public UUID program(TestTenants.TenantWithUsers t, List<String> jurisdictions) {
        var result = mvc.post().uri("/api/v1/programs").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "M.S. Clinical Mental Health Counseling", "licenseType", LICENSE,
                        "catalogYear", "2026-27", "jurisdictions", jurisdictions)))
                .exchange();
        assertThat(result).hasStatus(201);
        return UUID.fromString(read(result).get("id").asString());
    }

    /** Returns the course version id. The course has no credits; creating it put it in "Required courses" or "Electives" (D-588). */
    public UUID course(TestTenants.TenantWithUsers t, UUID programId, String code, boolean required) {
        return course(t, programId, code, required, null);
    }

    /** Returns the course version id; {@code credits} (semester credits) may be null. */
    public UUID course(TestTenants.TenantWithUsers t, UUID programId, String code, boolean required, String credits) {
        var body = new HashMap<String, Object>(Map.of("code", code, "title", "Course " + code, "required", required));
        if (credits != null) {
            body.put("credits", credits);
        }
        var result = mvc.post().uri("/api/v1/programs/" + programId + "/courses").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
        assertThat(result).hasStatus(201);
        return UUID.fromString(read(result).get("versions").get(0).get("id").asString());
    }

    /** The current version of a course of the program, by code. */
    public UUID courseVersionOf(TestTenants.TenantWithUsers t, UUID programId, String code) {
        var courses = read(mvc.get().uri("/api/v1/programs/" + programId + "/courses").with(user(t.reviewer())).exchange());
        for (JsonNode c : courses) {
            if (c.get("code").asString().equals(code)) {
                return UUID.fromString(c.get("versions").get(c.get("versions").size() - 1).get("id").asString());
            }
        }
        throw new AssertionError("No course " + code);
    }

    /** The courses of a program by code. */
    public Map<String, UUID> courseIds(TestTenants.TenantWithUsers t, UUID programId) {
        var courses = read(mvc.get().uri("/api/v1/programs/" + programId + "/courses").with(user(t.reviewer())).exchange());
        var ids = new HashMap<String, UUID>();
        courses.forEach(c -> ids.put(c.get("code").asString(), UUID.fromString(c.get("id").asString())));
        return ids;
    }

    /** Adds a pathway group and returns the pathway of its catalog year. */
    public JsonNode pathwayGroup(TestTenants.TenantWithUsers t, UUID programId, String catalogYear, String label, String kind,
                                 Integer minCount, String minCredits, List<UUID> courseIds) {
        var result = pathwayGroupResult(t, programId, catalogYear, label, kind, minCount, minCredits, courseIds, null);
        assertThat(result).hasStatus(201);
        return read(result);
    }

    public MvcTestResult pathwayGroupResult(TestTenants.TenantWithUsers t, UUID programId, String catalogYear, String label, String kind,
                                            Integer minCount, String minCredits, List<UUID> courseIds, Map<String, Object> citation) {
        var body = new HashMap<String, Object>(Map.of("catalogYear", catalogYear, "label", label, "kind", kind,
                "courseIds", courseIds.stream().map(UUID::toString).toList()));
        if (minCount != null) {
            body.put("minCount", minCount);
        }
        if (minCredits != null) {
            body.put("minCredits", minCredits);
        }
        if (citation != null) {
            body.put("citation", citation);
        }
        return mvc.post().uri("/api/v1/programs/" + programId + "/pathway-groups").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
    }

    /** The program's graduation paths for its own catalog year. */
    public JsonNode pathway(TestTenants.TenantWithUsers t, UUID programId) {
        return read(mvc.get().uri("/api/v1/programs/" + programId + "/pathway").with(user(t.reviewer())).exchange());
    }

    public MvcTestResult retirePathwayGroup(TestTenants.TenantWithUsers t, UUID groupId, String reason) {
        return mvc.post().uri("/api/v1/pathway-groups/" + groupId + "/retire").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reason", reason))).exchange();
    }

    /** Retires every active group of the program's catalog year, so that a test can describe the pathway itself. */
    public void clearPathway(TestTenants.TenantWithUsers t, UUID programId) {
        pathway(t, programId).get("groups")
                .forEach(g -> assertThat(retirePathwayGroup(t, UUID.fromString(g.get("id").asString()), "The test describes the pathway")).hasStatus(200));
    }

    /** Uploads a TXT document and runs ingest. Returns the document id. */
    public UUID document(TestTenants.TenantWithUsers t, UUID programId, String filename, String kind, UUID courseVersionId, String text) {
        return upload(t, programId, filename, "text/plain", kind, courseVersionId, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Uploads a PDF without a text layer and runs ingest; the OcrEngine of the test decides what its pages read as. */
    public UUID scannedDocument(TestTenants.TenantWithUsers t, UUID programId, String filename, String kind, UUID courseVersionId, byte[] pdf) {
        return upload(t, programId, filename, "application/pdf", kind, courseVersionId, pdf);
    }

    private UUID upload(TestTenants.TenantWithUsers t, UUID programId, String filename, String contentType, String kind,
                        UUID courseVersionId, byte[] bytes) {
        var request = multipart("/api/v1/programs/" + programId + "/documents")
                .file(new MockMultipartFile("file", filename, contentType, bytes))
                .param("kind", kind).with(user(t.contributor())).with(csrf());
        if (courseVersionId != null) {
            request.param("courseVersionId", courseVersionId.toString());
        }
        var result = mvc.perform(request);
        assertThat(result).hasStatus(202);
        runner.runPending();
        UUID id = UUID.fromString(read(result).get("documentId").asString());
        assertThat(read(mvc.get().uri("/api/v1/documents/" + id).with(user(t.contributor())).exchange())
                .get("ingest").get("status").asString()).isEqualTo("DONE");
        return id;
    }

    public MvcTestResult fact(TestTenants.TenantWithUsers t, UUID programId, String key, String value, UUID documentId, int page, String quote) {
        return mvc.post().uri("/api/v1/programs/" + programId + "/facts").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("key", key, "value", value, "documentId", documentId.toString(),
                        "pageNumber", page, "quote", quote)))
                .exchange();
    }

    /** Creates a rule version whose extraction (scripted) yields the given requirements JSON. Not approved. */
    public UUID ruleVersion(TestTenants.TenantWithUsers t, String jurisdiction, String label, String ruleText, String requirementsJson) {
        fake.script("EXTRACT:" + label, requirementsJson);
        var created = mvc.post().uri("/api/v1/rule-versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jurisdiction", jurisdiction, "licenseType", LICENSE, "label", label,
                        "sourceUrl", "https://example.invalid/" + jurisdiction, "effectiveFrom", LocalDate.of(2026, 1, 1).toString(), "legalStatus", "IN_FORCE", "datesBasis", "Fictional test data",
                        "text", ruleText)))
                .exchange();
        assertThat(created).hasStatus(202);
        UUID id = UUID.fromString(read(created).get("ruleVersionId").asString());
        runner.runPending();
        return id;
    }

    /** Creates a rule version as {@link #ruleVersion} does, and approves it. */
    public UUID approvedRuleVersion(TestTenants.TenantWithUsers t, String jurisdiction, String label, String ruleText, String requirementsJson) {
        UUID id = ruleVersion(t, jurisdiction, label, ruleText, requirementsJson);
        var approved = mvc.post().uri("/api/v1/rule-versions/" + id + "/requirements/approve").with(user(t.reviewer())).with(csrf()).exchange();
        assertThat(approved).hasStatus(200);
        return id;
    }

    /** Starts an assessment, runs the job, returns the assessment id. */
    public UUID runAssessment(TestTenants.TenantWithUsers t, UUID programId) {
        var started = mvc.post().uri("/api/v1/programs/" + programId + "/assessments").with(user(t.reviewer())).with(csrf()).exchange();
        assertThat(started).hasStatus(202);
        UUID id = UUID.fromString(read(started).get("assessmentId").asString());
        runner.runPending();
        return id;
    }

    public JsonNode assessment(TestTenants.TenantWithUsers t, UUID assessmentId) {
        return read(mvc.get().uri("/api/v1/assessments/" + assessmentId).with(user(t.reviewer())).exchange());
    }

    public JsonNode mappings(TestTenants.TenantWithUsers t, UUID assessmentId, String jurisdiction) {
        return read(mvc.get().uri("/api/v1/assessments/" + assessmentId + "/mappings?jurisdiction=" + jurisdiction)
                .with(user(t.reviewer())).exchange()).get("mappings");
    }

    public JsonNode mappingFor(JsonNode mappings, String ref) {
        for (JsonNode m : mappings) {
            if (m.get("requirement").get("ref").asString().equals(ref)) {
                return m;
            }
        }
        throw new AssertionError("No mapping for " + ref);
    }

    public String requirements(String prefix, List<Map<String, Object>> items) {
        var list = new java.util.ArrayList<Map<String, Object>>();
        int n = 1;
        for (var item : items) {
            var m = new HashMap<String, Object>();
            m.put("ref", prefix + "-" + n++);
            m.put("title", item.getOrDefault("title", "Requirement"));
            m.put("text", item.get("text"));
            m.put("type", item.get("type"));
            m.put("numeric_field", item.get("numeric_field"));
            m.put("numeric_operator", item.get("numeric_operator"));
            m.put("numeric_value", item.get("numeric_value"));
            m.put("search_terms", item.getOrDefault("search_terms", List.of()));
            list.add(m);
        }
        return json.writeValueAsString(Map.of("requirements", list));
    }

    public static Map<String, Object> subject(String title, String text) {
        var m = new HashMap<String, Object>();
        m.put("title", title); m.put("text", text); m.put("type", "SUBJECT");
        m.put("numeric_field", null); m.put("numeric_operator", null); m.put("numeric_value", null);
        return m;
    }

    /** The same requirement with search terms (F7c); the list a model would have proposed or a reviewer typed. */
    public static Map<String, Object> withTerms(Map<String, Object> requirement, String... terms) {
        var m = new HashMap<>(requirement);
        m.put("search_terms", List.of(terms));
        return m;
    }

    public static Map<String, Object> specific(String title, String text) {
        var m = subject(title, text);
        m.put("type", "JURISDICTION_SPECIFIC");
        return m;
    }

    public static Map<String, Object> numeric(String type, String field, String operator, int value, String text) {
        var m = new HashMap<String, Object>();
        m.put("title", field); m.put("text", text); m.put("type", type);
        m.put("numeric_field", field); m.put("numeric_operator", operator); m.put("numeric_value", value);
        return m;
    }

    public void fakeScript(String key, String... responses) {
        fake.script(key, responses);
    }

    public String matchOutput(String finding, UUID documentId, Integer page, String quote, String rationale) {
        var m = new HashMap<String, Object>();
        m.put("finding", finding);
        m.put("document_id", documentId == null ? null : documentId.toString());
        m.put("page_number", page);
        m.put("quote", quote);
        m.put("rationale", rationale);
        return json.writeValueAsString(m);
    }
}
