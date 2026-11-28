package com.lexvay.rule;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.audit.AuditRepository;
import com.lexvay.auth.LexvayUser;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.document.DocumentRepository;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmCallRecorder;
import com.lexvay.llm.LlmClient;
import com.lexvay.llm.LlmPurpose;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class RuleVersionApiTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired AuditRepository audit;
    @Autowired JobRunner runner;
    @Autowired LlmClient llmClient;
    @Autowired LlmCallRecorder llmCalls;
    @Autowired RuleVersionRepository ruleVersions;
    @Autowired JurisdictionRepository jurisdictions;
    @Autowired DocumentRepository documents;

    FakeLlmClient fake;
    TestTenants.TenantWithUsers t;

    public static final String RULE_TEXT = """
            Demo rule set. Fictional. Prototype data.
            R156-60-302. (1) An applicant shall have completed a graduate degree of at least 60 semester hours.
            (2) The degree shall include coursework in professional ethics and in human growth and development.
            (3) The applicant shall have completed at least 600 hours of supervised internship.
            """;

    public static String validOutput(String prefix) {
        return """
                {"requirements":[
                  {"ref":"%1$s-1","title":"Credit hours","text":"graduate degree of at least 60 semester hours","type":"NUMERIC",
                   "numeric_field":"TOTAL_CREDITS","numeric_operator":">=","numeric_value":60,"search_terms":[]},
                  {"ref":"%1$s-2","title":"Professional ethics","text":"coursework in professional ethics","type":"SUBJECT",
                   "numeric_field":null,"numeric_operator":null,"numeric_value":null,
                   "search_terms":["Code of Ethics","confidentiality","informed consent"]},
                  {"ref":"%1$s-3","title":"Human growth","text":"coursework in human growth and development","type":"SUBJECT",
                   "numeric_field":null,"numeric_operator":null,"numeric_value":null,
                   "search_terms":["lifespan development","aging"]},
                  {"ref":"%1$s-4","title":"Internship hours","text":"at least 600 hours of supervised internship","type":"SUPERVISED_HOURS",
                   "numeric_field":"INTERNSHIP_HOURS","numeric_operator":">=","numeric_value":600,"search_terms":["fieldwork"]}
                ]}""".formatted(prefix);
    }

    static final String INVALID_OUTPUT = """
            {"requirements":[{"ref":"UT-1","title":"Credit hours","text":"60 hours","type":"NUMERIC",
             "numeric_field":null,"numeric_operator":null,"numeric_value":null,"search_terms":[]}]}""";

    @BeforeEach
    void setUp() {
        t = tenants.create("rules");
        fake = (FakeLlmClient) llmClient;
        runner.runPending();
        fake.reset();
    }

    private String body(String label, String jurisdiction, UUID supersedes) {
        var map = new java.util.HashMap<String, Object>(Map.of(
                "jurisdiction", jurisdiction, "licenseType", "CLINICAL_MENTAL_HEALTH_COUNSELING", "label", label,
                "sourceUrl", "https://example.invalid/demo/" + label.replace(' ', '-'),
                "effectiveFrom", LocalDate.of(2026, 7, 1).toString(), "legalStatus", "IN_FORCE", "datesBasis", "Fictional test data", "text", RULE_TEXT + "\nLabel: " + label));
        if (supersedes != null) {
            map.put("supersedesId", supersedes.toString());
        }
        return json.writeValueAsString(map);
    }

    private MvcTestResult create(LexvayUser as, String label, String jurisdiction, UUID supersedes) {
        return mvc.post().uri("/api/v1/rule-versions").with(user(as)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(label, jurisdiction, supersedes)).exchange();
    }

    private JsonNode read(MvcTestResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private UUID createAndExtract(String label, String jurisdiction) throws Exception {
        fake.script("EXTRACT:" + label, validOutput(jurisdiction));
        var created = create(t.contributor(), label, jurisdiction, null);
        assertThat(created).hasStatus(202);
        UUID id = UUID.fromString(read(created).get("ruleVersionId").asString());
        runner.runPending();
        return id;
    }

    private Optional<RuleVersion> inForce(UUID jurisdictionId, LocalDate asOf) {
        return Optional.ofNullable(TenantContext.runAs(t.tenantId(), () -> ruleVersions.findInForce(List.of(jurisdictionId),
                "CLINICAL_MENTAL_HEALTH_COUNSELING", asOf)).get(jurisdictionId));
    }

    private MvcTestResult approve(LexvayUser as, UUID id) {
        return mvc.post().uri("/api/v1/rule-versions/" + id + "/requirements/approve").with(user(as)).with(csrf()).exchange();
    }

    @Test
    void createStoresRuleTextAndExtractsRequirements() throws Exception {
        String label = "Demo rule set Utah 2026";
        fake.script("EXTRACT:" + label, validOutput("UT"));
        var created = create(t.contributor(), label, "UT", null);
        assertThat(created).hasStatus(202);
        UUID id = UUID.fromString(read(created).get("ruleVersionId").asString());
        UUID jobId = UUID.fromString(read(created).get("jobId").asString());

        var beforeRun = read(mvc.get().uri("/api/v1/rule-versions/" + id).with(user(t.reviewer())).exchange());
        assertThat(beforeRun.get("extraction").get("status").asString()).isEqualTo("QUEUED");
        assertThat(beforeRun.get("requirementCount").asInt()).isZero();
        assertThat(beforeRun.get("approval").isNull()).isTrue();
        UUID documentId = UUID.fromString(beforeRun.get("documentId").asString());
        var pages = TenantContext.runAs(t.tenantId(), () -> documents.pages(documentId));
        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).text()).contains("R156-60-302");
        assertThat(TenantContext.runAs(t.tenantId(), () -> documents.findById(documentId)).orElseThrow().kind().name()).isEqualTo("RULE_TEXT");

        runner.runPending();

        var after = read(mvc.get().uri("/api/v1/rule-versions/" + id).with(user(t.reviewer())).exchange());
        assertThat(after.get("extraction").get("status").asString()).isEqualTo("DONE");
        assertThat(after.get("requirementCount").asInt()).isEqualTo(4);
        var requirements = read(mvc.get().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).exchange());
        assertThat(requirements.get("approved").asBoolean()).isFalse();
        assertThat(requirements.get("requirements")).hasSize(4);
        var hours = requirements.get("requirements").get(3);
        assertThat(hours.get("type").asString()).isEqualTo("SUPERVISED_HOURS");
        assertThat(hours.get("numericField").asString()).isEqualTo("INTERNSHIP_HOURS");
        assertThat(hours.get("numericValue").asInt()).isEqualTo(600);
        assertThat(requirements.get("requirements").get(0).get("searchTerms")).as("scripted as an empty list").isEmpty();
        assertThat(requirements.get("requirements").get(1).get("searchTerms")).as("the model's terms, normalised")
                .extracting(JsonNode::asString).containsExactly("code of ethics", "confidentiality", "informed consent");
        assertThat(hours.get("searchTerms")).extracting(JsonNode::asString).containsExactly("fieldwork");

        assertThat(fake.callCount(LlmPurpose.EXTRACT_REQUIREMENTS)).isEqualTo(1);
        var call = fake.calls().get(0);
        assertThat(call.userPrompt()).contains("R156-60-302").contains("Jurisdiction: UT");
        assertThat(call.outputSchema().path("properties").has("requirements")).isTrue();
        assertThat(TenantContext.runAs(t.tenantId(), () -> llmCalls.countForRuleVersion(id))).isEqualTo(1);

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(id));
        assertThat(entries).extracting(e -> e.action()).containsExactlyInAnyOrder("RULE_VERSION_CREATED", "REQUIREMENTS_EXTRACTED");
        var extracted = entries.stream().filter(e -> e.action().equals("REQUIREMENTS_EXTRACTED")).findFirst().orElseThrow();
        assertThat(extracted.detail().get("jobId")).isEqualTo(jobId.toString());
        assertThat(extracted.detail().get("promptVersion")).as("the prompt that asks for search terms").isEqualTo("extract-v2");
        assertThat(call.systemPrompt()).contains("search_terms");
    }

    @Test
    void invalidOutputIsRetriedOnceWithTheErrors() throws Exception {
        String label = "Demo rule set Colorado retry";
        fake.script("EXTRACT:" + label, INVALID_OUTPUT, validOutput("CO"));
        var created = create(t.admin(), label, "CO", null);
        UUID id = UUID.fromString(read(created).get("ruleVersionId").asString());
        runner.runPending();

        assertThat(fake.callCount(LlmPurpose.EXTRACT_REQUIREMENTS)).isEqualTo(2);
        assertThat(fake.calls().get(1).userPrompt()).contains("previous output was rejected").contains("numeric_field");
        assertThat(TenantContext.runAs(t.tenantId(), () -> llmCalls.countForRuleVersion(id))).isEqualTo(2);
        var view = read(mvc.get().uri("/api/v1/rule-versions/" + id).with(user(t.reviewer())).exchange());
        assertThat(view.get("extraction").get("status").asString()).isEqualTo("DONE");
        assertThat(view.get("requirementCount").asInt()).isEqualTo(4);
    }

    @Test
    void invalidOutputTwiceFailsTheJobAndKeepsBothCalls() throws Exception {
        String label = "Demo rule set Arizona broken";
        fake.script("EXTRACT:" + label, INVALID_OUTPUT, "{\"requirements\": \"nope\"}");
        var created = create(t.admin(), label, "AZ", null);
        UUID id = UUID.fromString(read(created).get("ruleVersionId").asString());
        runner.runPending();

        var view = read(mvc.get().uri("/api/v1/rule-versions/" + id).with(user(t.reviewer())).exchange());
        assertThat(view.get("extraction").get("status").asString()).isEqualTo("FAILED");
        assertThat(view.get("extraction").get("error").asString()).contains("invalid output twice");
        assertThat(view.get("requirementCount").asInt()).isZero();
        assertThat(TenantContext.runAs(t.tenantId(), () -> llmCalls.countForRuleVersion(id))).as("raw output kept despite rollback").isEqualTo(2);
        assertThat(approve(t.reviewer(), id)).hasStatus(409);
    }

    @Test
    void approvalIsAuditedAndHappensOnce() throws Exception {
        UUID id = createAndExtract("Demo rule set Texas", "TX");
        UUID jurisdictionId = jurisdictions.findByCodes(List.of("TX")).get(0).id();
        assertThat(inForce(jurisdictionId, LocalDate.of(2026, 9, 1))).as("unapproved: cannot be assessed").isEmpty();

        var approved = approve(t.reviewer(), id);
        assertThat(approved).hasStatus(200);
        assertThat(read(approved).get("approval").get("by").asString()).isEqualTo(t.reviewer().id().toString());
        assertThat(read(mvc.get().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).exchange())
                .get("approved").asBoolean()).isTrue();

        assertThat(inForce(jurisdictionId, LocalDate.of(2026, 9, 1))).map(RuleVersion::id).contains(id);
        assertThat(inForce(jurisdictionId, LocalDate.of(2026, 6, 1))).as("not yet effective").isEmpty();

        assertThat(approve(t.signer(), id)).hasStatus(409);
        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(id));
        assertThat(entries).extracting(e -> e.action()).contains("REQUIREMENTS_APPROVED");
        assertThat(entries.stream().filter(e -> e.action().equals("REQUIREMENTS_APPROVED")).findFirst().orElseThrow().userId())
                .isEqualTo(t.reviewer().id());
    }

    @Test
    void reviewerCanReplaceRequirementsBeforeApproval() throws Exception {
        UUID id = createAndExtract("Demo rule set California", "CA");
        String replacement = """
                {"requirements":[
                  {"ref":"CA-1","title":"Credit hours","text":"at least 60 semester hours","type":"NUMERIC",
                   "numeric_field":"TOTAL_CREDITS","numeric_operator":">=","numeric_value":60},
                  {"ref":"CA-9","title":"Telehealth","text":"training in telehealth practice","type":"JURISDICTION_SPECIFIC",
                   "numeric_field":null,"numeric_operator":null,"numeric_value":null}
                ]}""";
        var replaced = mvc.put().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(replacement).exchange();
        assertThat(replaced).hasStatus(200);
        assertThat(read(replaced).get("requirements")).hasSize(2);
        assertThat(read(replaced).get("requirements").get(1).get("ref").asString()).isEqualTo("CA-9");

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(id));
        var replacedEvent = entries.stream().filter(e -> e.action().equals("REQUIREMENTS_REPLACED")).findFirst().orElseThrow();
        assertThat((List<?>) replacedEvent.detail().get("before")).hasSize(4);
        assertThat((List<?>) replacedEvent.detail().get("after")).hasSize(2);
        assertThat(replacedEvent.userId()).isEqualTo(t.reviewer().id());

        var bad = mvc.put().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(INVALID_OUTPUT).exchange();
        assertThat(bad).hasStatus(400);
        assertThat(read(bad).get("detail").asString()).contains("numeric_field");
    }

    @Test
    void replaceStoresSearchTermsNormalisedAndTreatsAbsentTermsAsNone() throws Exception {
        UUID id = createAndExtract("Demo rule set California terms", "CA");
        String replacement = """
                {"requirements":[
                  {"ref":"CA-1","title":"Telehealth","text":"training in telehealth practice","type":"JURISDICTION_SPECIFIC",
                   "numeric_field":null,"numeric_operator":null,"numeric_value":null,
                   "search_terms":["  Online Counseling ","online counseling","videoconferencing","telehealth","California licensing","dsm 5"]},
                  {"ref":"CA-2","title":"Ethics","text":"coursework in professional ethics","type":"SUBJECT",
                   "numeric_field":null,"numeric_operator":null,"numeric_value":null}
                ]}""";

        var replaced = mvc.put().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(replacement).exchange();

        assertThat(replaced).hasStatus(200);
        var requirements = read(replaced).get("requirements");
        assertThat(requirements.get(0).get("searchTerms")).extracting(JsonNode::asString).containsExactly("online counseling", "videoconferencing");
        assertThat(requirements.get(1).get("searchTerms")).as("absent means none").isEmpty();
        var stored = read(mvc.get().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).exchange());
        assertThat(stored.get("requirements").get(0).get("searchTerms")).extracting(JsonNode::asString)
                .containsExactly("online counseling", "videoconferencing");
        var event = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(id)).stream()
                .filter(e -> e.action().equals("REQUIREMENTS_REPLACED")).findFirst().orElseThrow();
        assertThat(event.detail().get("after").toString()).contains("online counseling");
    }

    @Test
    void replaceRejectsMoreThanTwelveTermsNamingTheRowAndKeepsTheStoredList() throws Exception {
        UUID id = createAndExtract("Demo rule set California too many terms", "CA");
        String twelve = "\"a1x\",\"a2x\",\"a3x\",\"a4x\",\"a5x\",\"a6x\",\"a7x\",\"a8x\",\"a9x\",\"b1x\",\"b2x\",\"b3x\"";
        String body = """
                {"requirements":[
                  {"ref":"CA-1","title":"Telehealth","text":"training in telehealth practice","type":"SUBJECT",
                   "numeric_field":null,"numeric_operator":null,"numeric_value":null,"search_terms":[%s]},
                  {"ref":"CA-2","title":"Ethics","text":"coursework in professional ethics","type":"SUBJECT",
                   "numeric_field":null,"numeric_operator":null,"numeric_value":null,"search_terms":[%s,"b4x"]}
                ]}""".formatted(twelve, twelve);

        var rejected = mvc.put().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(rejected).hasStatus(400);
        assertThat(read(rejected).get("detail").asString()).contains("requirements[1].search_terms must contain at most 12 terms")
                .doesNotContain("requirements[0]");
        assertThat(read(mvc.get().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).exchange())
                .get("requirements")).as("the extracted list is untouched").hasSize(4);
        String tooLong = """
                {"requirements":[{"ref":"CA-1","title":"Ethics","text":"coursework in professional ethics","type":"SUBJECT",
                 "numeric_field":null,"numeric_operator":null,"numeric_value":null,"search_terms":["%s"]}]}""".formatted("x".repeat(41));
        var longTerm = mvc.put().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(tooLong).exchange();
        assertThat(longTerm).hasStatus(400);
        assertThat(read(longTerm).get("detail").asString()).contains("requirements[0].search_terms[0] must be at most 40 characters");
    }

    @Test
    void searchTermsOfAnApprovedListCannotBeChanged() throws Exception {
        UUID id = createAndExtract("Demo rule set California approved terms", "CA");
        assertThat(approve(t.reviewer(), id)).hasStatus(200);
        String body = """
                {"requirements":[{"ref":"CA-1","title":"Ethics","text":"coursework in professional ethics","type":"SUBJECT",
                 "numeric_field":null,"numeric_operator":null,"numeric_value":null,"search_terms":["code of ethics"]}]}""";

        assertThat(mvc.put().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange()).hasStatus(409);
        assertThat(read(mvc.get().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).exchange())
                .get("requirements").get(1).get("searchTerms")).extracting(JsonNode::asString)
                .containsExactly("code of ethics", "confidentiality", "informed consent");
    }

    @Test
    void rolesTenancyAndValidation() throws Exception {
        UUID id = createAndExtract("Demo rule set Utah roles", "UT");
        assertThat(approve(t.contributor(), id)).as("contributor cannot approve").hasStatus(403);
        assertThat(create(t.reviewer(), "x", "UT", null)).as("reviewer cannot create").hasStatus(403);
        var other = tenants.create("other");
        assertThat(mvc.get().uri("/api/v1/rule-versions/" + id).with(user(other.admin())).exchange()).hasStatus(404);
        assertThat(approve(other.admin(), id)).hasStatus(404);
        assertThat(create(t.admin(), "Unknown state", "ZZ", null)).hasStatus(400);
        assertThat(create(t.admin(), "Bad supersede", "CO", id)).as("different jurisdiction").hasStatus(400);
        var list = read(mvc.get().uri("/api/v1/rule-versions").with(user(t.reviewer())).exchange());
        assertThat(list).hasSize(1);
        assertThat(read(mvc.get().uri("/api/v1/rule-versions").with(user(other.admin())).exchange())).isEmpty();
    }

    @Test
    void supersedingVersionIsStoredWithItsLink() throws Exception {
        UUID first = createAndExtract("Demo rule set Colorado v1", "CO");
        approve(t.reviewer(), first);
        fake.script("EXTRACT:Demo rule set Colorado v2", validOutput("CO"));
        var second = create(t.admin(), "Demo rule set Colorado v2", "CO", first);
        assertThat(second).hasStatus(202);
        UUID secondId = UUID.fromString(read(second).get("ruleVersionId").asString());
        runner.runPending();
        assertThat(read(mvc.get().uri("/api/v1/rule-versions/" + secondId).with(user(t.reviewer())).exchange())
                .get("supersedesId").asString()).isEqualTo(first.toString());
    }
}
