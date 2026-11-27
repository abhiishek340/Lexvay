package com.lexvay.common;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.review.ReviewFixtures;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Every endpoint, hit with malformed input (D-096): a path id that is not a UUID, a missing field, an
 * oversized string, an unknown enum value, a control character the database cannot store. Each must answer a
 * 4xx problem detail and never a 500. {@link #everyEndpointHasARow} fails when an endpoint is added without
 * a row here, so new endpoints cannot skip input validation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InputValidationTableTest extends AbstractDatabaseTest {

    private static final String ID = "7d1f0c2e-5b7a-4c1e-9d3a-0a1b2c3d4e5f";
    /** Placeholders for records that exist, so that validation deeper than the controller is reached too. */
    private static final String PROGRAM = "{program}";
    private static final String DOCUMENT = "{document}";
    private static final String RULE = "{rule}";
    private static final String MAPPING = "{mapping}";
    private static final String REQUESTED_MAPPING = "{requestedMapping}";
    private static final String BAD_ID = "not-a-uuid";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private enum As { ADMIN, SIGNER, ANONYMOUS }

    /**
     * One malformed request. {@code endpoint} is the handler mapping it exercises, in the same
     * "METHOD /pattern" form {@code Rule9_AuditCoverageTest} uses; filter endpoints (login) use their path.
     */
    private record Row(String name, String endpoint, HttpMethod method, String uri, String body, String contentType,
                       int expectedStatus, As as) {

        @Override
        public String toString() {
            return name;
        }
    }

    private static final ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder().build();

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired JobRunner runner;
    @Autowired LlmClient llm;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    TestTenants.TenantWithUsers t;
    Map<String, String> existing;

    @BeforeAll
    void createRecords() throws Exception {
        t = tenants.create("validation");
        var fx = new AssessmentFixtures(mvc, json, runner, (FakeLlmClient) llm);
        var scenario = new ReviewFixtures(mvc, json, fx).californiaScenario(t, "validation");
        UUID document = fx.document(t, scenario.programId(), "plan.txt", "DEGREE_PLAN", null, "Fictional. Prototype data. Total credits: 60");
        var rule = mvc.post().uri("/api/v1/rule-versions").with(user(t.admin())).with(csrf()).contentType(JSON)
                .content(json(Map.of("jurisdiction", "CA", "licenseType", "L", "label", "Demo rule set validation",
                        "sourceUrl", "https://example.test/rule", "effectiveFrom", "2026-01-01", "text", "Fictional rule text")))
                .exchange();
        new ReviewFixtures(mvc, json, fx).review(t.reviewer(), scenario.ambiguousMapping(), "EVIDENCE_REQUESTED", "Send the schedule");
        existing = Map.of(PROGRAM, scenario.programId().toString(), DOCUMENT, document.toString(),
                MAPPING, scenario.foundMapping().toString(), REQUESTED_MAPPING, scenario.ambiguousMapping().toString(),
                RULE, fx.read(rule).get("ruleVersionId").asString());
    }

    private String fill(String template) {
        String filled = template;
        for (var entry : existing.entrySet()) {
            filled = filled.replace(entry.getKey(), entry.getValue());
        }
        return filled;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void malformedInputIsRefusedWithAProblemDetail(Row row) throws Exception {
        var builder = request(row.method(), java.net.URI.create(fill(row.uri()))).with(csrf());
        switch (row.as()) {
            case ADMIN -> builder.with(user(t.admin()));
            case SIGNER -> builder.with(user(t.signer()));
            case ANONYMOUS -> { }
        }
        if (row.body() != null) {
            builder.contentType(row.contentType()).content(fill(row.body()));
        }
        var result = mvc.perform(builder);

        assertThat(result.getResponse().getStatus()).as("%s", row.name()).isEqualTo(row.expectedStatus());
        if (row.expectedStatus() >= 400) {
            assertThat(result.getResponse().getContentType()).contains("application/problem+json");
            JsonNode problem = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(problem.get("status").asInt()).isEqualTo(row.expectedStatus());
            assertThat(problem.get("detail").asString()).isNotBlank();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multipartRows")
    void malformedUploadIsRefusedWithAProblemDetail(String name, String uri, Map<String, String> params,
                                                    MockMultipartFile file, int expectedStatus) throws Exception {
        var builder = multipart(java.net.URI.create(fill(uri)));
        if (file != null) {
            builder.file(file);
        }
        params.forEach((key, value) -> builder.param(key, fill(value)));
        var result = mvc.perform(builder.with(user(t.admin())).with(csrf()));

        assertThat(result.getResponse().getStatus()).as(name).isEqualTo(expectedStatus);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
    }

    @Test
    void everyEndpointHasARow() {
        Set<String> handled = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            for (RequestMethod verb : info.getMethodsCondition().getMethods()) {
                for (var pattern : info.getPathPatternsCondition().getPatterns()) {
                    handled.add(verb + " " + pattern.getPatternString());
                }
            }
        });
        Set<String> covered = new TreeSet<>();
        rows().forEach(row -> covered.add(row.endpoint()));
        covered.add("POST /api/v1/programs/{programId}/documents");
        assertThat(covered).containsAll(handled);
    }

    /** Upload rows. Every file has distinct content, because identical content in a program is a conflict. */
    static Stream<Arguments> multipartRows() {
        String upload = "/api/v1/programs/" + PROGRAM + "/documents";
        return Stream.of(
                Arguments.of("upload: program id is not a UUID", "/api/v1/programs/" + BAD_ID + "/documents",
                        Map.of("kind", "SYLLABUS"), file("a.txt"), 400),
                Arguments.of("upload: file part missing", upload, Map.of("kind", "SYLLABUS"), null, 400),
                Arguments.of("upload: kind missing", upload, Map.of(), file("b.txt"), 400),
                Arguments.of("upload: unknown kind", upload, Map.of("kind", "NOVEL"), file("c.txt"), 400),
                Arguments.of("upload: course version id is not a UUID", upload,
                        Map.of("kind", "SYLLABUS", "courseVersionId", BAD_ID), file("d.txt"), 400),
                Arguments.of("upload: oversized file name", upload, Map.of("kind", "SYLLABUS"),
                        file("a".repeat(300) + ".txt"), 400),
                Arguments.of("upload: control character in file name", upload, Map.of("kind", "SYLLABUS"),
                        file("a\u0000b.txt"), 400),
                Arguments.of("upload: line break in file name", upload, Map.of("kind", "SYLLABUS"),
                        file("a\r\nb.txt"), 400));
    }

    private static MockMultipartFile file(String filename) {
        return new MockMultipartFile("file", filename, "text/plain", ("content of " + filename).getBytes());
    }

    static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        BodyRows bodies = new BodyRows(rows);

        // --- authentication (filter endpoints and the session endpoints) ---
        rows.add(row("login: username missing", "POST /api/v1/auth/login", HttpMethod.POST, "/api/v1/auth/login",
                "password=x", MediaType.APPLICATION_FORM_URLENCODED_VALUE, 401));
        rows.add(row("login: control character in username", "POST /api/v1/auth/login", HttpMethod.POST,
                "/api/v1/auth/login", "username=a%00b%40x.test&password=x", MediaType.APPLICATION_FORM_URLENCODED_VALUE, 401));
        rows.add(row("login: oversized password", "POST /api/v1/auth/login", HttpMethod.POST, "/api/v1/auth/login",
                "username=a%40x.test&password=" + "p".repeat(200_000), MediaType.APPLICATION_FORM_URLENCODED_VALUE, 401));
        rows.add(row("session: hostile query parameter is ignored", "GET /api/v1/auth/session", HttpMethod.GET,
                "/api/v1/auth/session?q=%00%ff", null, null, 200));
        rows.add(row("me: hostile query parameter is ignored", "GET /api/v1/auth/me", HttpMethod.GET,
                "/api/v1/auth/me?q=%00%ff", null, null, 200));
        rows.add(row("users: hostile query parameter is ignored", "GET /api/v1/users", HttpMethod.GET,
                "/api/v1/users?q=%00%ff", null, null, 200));

        // --- test-only probe of the logging context (src/test, observability) ---
        rows.add(row("mdc probe: hostile query parameter is ignored", "GET /api/v1/test-support/mdc", HttpMethod.GET,
                "/api/v1/test-support/mdc?q=%00%ff", null, null, 200));

        // --- jurisdictions, jobs, audit ---
        rows.add(row("jurisdictions: hostile query parameter is ignored", "GET /api/v1/jurisdictions", HttpMethod.GET,
                "/api/v1/jurisdictions?q=%00%ff", null, null, 200));
        rows.add(row("job: id is not a UUID", "GET /api/v1/jobs/{id}", HttpMethod.GET, "/api/v1/jobs/" + BAD_ID, null, null, 400));
        rows.add(row("audit: limit is not a number", "GET /api/v1/audit", HttpMethod.GET, "/api/v1/audit?limit=abc", null, null, 400));
        rows.add(row("audit: limit below minimum", "GET /api/v1/audit", HttpMethod.GET, "/api/v1/audit?limit=0", null, null, 400));
        rows.add(row("audit: limit above maximum", "GET /api/v1/audit", HttpMethod.GET, "/api/v1/audit?limit=501", null, null, 400));
        rows.add(row("audit: negative offset", "GET /api/v1/audit", HttpMethod.GET, "/api/v1/audit?offset=-1", null, null, 400));
        rows.add(row("audit: offset overflows an int", "GET /api/v1/audit", HttpMethod.GET, "/api/v1/audit?offset=99999999999", null, null, 400));

        rows.add(row("audit: entity id is not a UUID", "GET /api/v1/audit", HttpMethod.GET, "/api/v1/audit?entityId=" + BAD_ID, null, null, 400));
        rows.add(row("audit: user id is not a UUID", "GET /api/v1/audit", HttpMethod.GET, "/api/v1/audit?userId=" + BAD_ID, null, null, 400));
        rows.add(row("audit: from is not a timestamp", "GET /api/v1/audit", HttpMethod.GET, "/api/v1/audit?from=yesterday", null, null, 400));
        rows.add(row("audit: to is not a timestamp", "GET /api/v1/audit", HttpMethod.GET, "/api/v1/audit?to=%00", null, null, 400));
        rows.add(row("audit: control character in action filter", "GET /api/v1/audit", HttpMethod.GET,
                "/api/v1/audit?action=a%00b", null, null, 400));

        // --- programs ---
        Map<String, Object> program = Map.of("name", "P", "licenseType", "L", "catalogYear", "2026-27", "jurisdictions", List.of("CA"));
        bodies.post("programs: name missing", "POST /api/v1/programs", "/api/v1/programs", without(program, "name"));
        bodies.post("programs: oversized name", "POST /api/v1/programs", "/api/v1/programs", with(program, "name", "n".repeat(201)));
        bodies.post("programs: control character in name", "POST /api/v1/programs", "/api/v1/programs", with(program, "name", "a\u0000b"));
        bodies.post("programs: jurisdiction is not two capital letters", "POST /api/v1/programs", "/api/v1/programs",
                with(program, "jurisdictions", List.of("California")));
        bodies.post("programs: no jurisdictions", "POST /api/v1/programs", "/api/v1/programs", with(program, "jurisdictions", List.of()));
        bodies.post("programs: jurisdictions is not a list", "POST /api/v1/programs", "/api/v1/programs", with(program, "jurisdictions", "CA"));
        rows.add(rawJson("programs: body is not JSON", "POST /api/v1/programs", HttpMethod.POST, "/api/v1/programs", "{not json", 400));
        rows.add(rawJson("programs: body nests deeper than the parser allows", "POST /api/v1/programs", HttpMethod.POST,
                "/api/v1/programs", "[".repeat(5000), 400));
        rows.add(row("programs: body sent as plain text", "POST /api/v1/programs", HttpMethod.POST, "/api/v1/programs",
                "name=P", MediaType.TEXT_PLAIN_VALUE, 415));
        rows.add(row("programs: id is not a UUID", "GET /api/v1/programs/{id}", HttpMethod.GET, "/api/v1/programs/" + BAD_ID, null, null, 400));
        rows.add(row("programs: list ignores a hostile query parameter", "GET /api/v1/programs", HttpMethod.GET,
                "/api/v1/programs?q=%00%ff", null, null, 200));

        // --- courses and course versions ---
        Map<String, Object> course = Map.of("code", "COUN 605", "title", "Ethics", "required", true);
        String courses = "/api/v1/programs/" + ID + "/courses";
        rows.add(row("courses create: program id is not a UUID", "POST /api/v1/programs/{programId}/courses", HttpMethod.POST,
                "/api/v1/programs/" + BAD_ID + "/courses", json(course), JSON, 400));
        bodies.post("courses create: required flag missing", "POST /api/v1/programs/{programId}/courses", courses, without(course, "required"));
        bodies.post("courses create: oversized code", "POST /api/v1/programs/{programId}/courses", courses, with(course, "code", "c".repeat(31)));
        bodies.post("courses create: required flag is not a boolean", "POST /api/v1/programs/{programId}/courses", courses, with(course, "required", "maybe"));
        rows.add(row("courses list: program id is not a UUID", "GET /api/v1/programs/{programId}/courses", HttpMethod.GET,
                "/api/v1/programs/" + BAD_ID + "/courses", null, null, 400));
        Map<String, Object> version = Map.of("catalogYear", "2026-27", "required", true);
        String versions = "/api/v1/courses/" + ID + "/versions";
        rows.add(row("course version: course id is not a UUID", "POST /api/v1/courses/{courseId}/versions", HttpMethod.POST,
                "/api/v1/courses/" + BAD_ID + "/versions", json(version), JSON, 400));
        bodies.post("course version: catalog year missing", "POST /api/v1/courses/{courseId}/versions", versions, without(version, "catalogYear"));
        bodies.post("course version: superseded id is not a UUID", "POST /api/v1/courses/{courseId}/versions", versions,
                with(version, "supersedesId", BAD_ID));
        bodies.post("course version: oversized catalog year", "POST /api/v1/courses/{courseId}/versions", versions,
                with(version, "catalogYear", "y".repeat(21)));

        // --- program facts ---
        Map<String, Object> fact = Map.of("key", "TOTAL_CREDITS", "value", 60, "documentId", DOCUMENT, "pageNumber", 1, "quote", "Total credits: 60");
        String facts = "/api/v1/programs/" + PROGRAM + "/facts";
        rows.add(row("facts create: program id is not a UUID", "POST /api/v1/programs/{programId}/facts", HttpMethod.POST,
                "/api/v1/programs/" + BAD_ID + "/facts", json(fact), JSON, 400));
        bodies.post("facts create: unknown key", "POST /api/v1/programs/{programId}/facts", facts, with(fact, "key", "SHOE_SIZE"));
        bodies.post("facts create: negative value", "POST /api/v1/programs/{programId}/facts", facts, with(fact, "value", -1));
        bodies.post("facts create: value is not a number", "POST /api/v1/programs/{programId}/facts", facts, with(fact, "value", "sixty"));
        bodies.post("facts create: quote missing", "POST /api/v1/programs/{programId}/facts", facts, without(fact, "quote"));
        bodies.post("facts create: document id is not a UUID", "POST /api/v1/programs/{programId}/facts", facts, with(fact, "documentId", BAD_ID));
        bodies.post("facts create: page number zero", "POST /api/v1/programs/{programId}/facts", facts, with(fact, "pageNumber", 0));
        bodies.post("facts create: control character in quote", "POST /api/v1/programs/{programId}/facts", facts, with(fact, "quote", "a\u0000b"));
        rows.add(row("facts list: program id is not a UUID", "GET /api/v1/programs/{programId}/facts", HttpMethod.GET,
                "/api/v1/programs/" + BAD_ID + "/facts", null, null, 400));

        // --- documents ---
        rows.add(row("document: id is not a UUID", "GET /api/v1/documents/{id}", HttpMethod.GET, "/api/v1/documents/" + BAD_ID, null, null, 400));
        String page = "GET /api/v1/documents/{id}/pages/{pageNumber}";
        rows.add(row("document page: id is not a UUID", page, HttpMethod.GET, "/api/v1/documents/" + BAD_ID + "/pages/1", null, null, 400));
        rows.add(row("document page: page number is not a number", page, HttpMethod.GET, "/api/v1/documents/" + DOCUMENT + "/pages/one", null, null, 400));
        rows.add(row("document page: page number overflows an int", page, HttpMethod.GET,
                "/api/v1/documents/" + DOCUMENT + "/pages/99999999999", null, null, 400));
        rows.add(row("document page: page number zero does not exist", page, HttpMethod.GET, "/api/v1/documents/" + DOCUMENT + "/pages/0", null, null, 404));
        String file = "GET /api/v1/documents/{id}/file";
        rows.add(row("document file: id is not a UUID", file, HttpMethod.GET, "/api/v1/documents/" + BAD_ID + "/file", null, null, 400));
        rows.add(row("document file: unknown id is not found", file, HttpMethod.GET, "/api/v1/documents/" + ID + "/file", null, null, 404));
        rows.add(row("document file: hostile query parameter is ignored", file, HttpMethod.GET,
                "/api/v1/documents/" + DOCUMENT + "/file?q=%00%ff", null, null, 200));

        // --- program overview and assessments of a program ---
        rows.add(row("overview: program id is not a UUID", "GET /api/v1/programs/{programId}/overview", HttpMethod.GET,
                "/api/v1/programs/" + BAD_ID + "/overview", null, null, 400));
        rows.add(row("assessments of a program: program id is not a UUID", "GET /api/v1/programs/{programId}/assessments", HttpMethod.GET,
                "/api/v1/programs/" + BAD_ID + "/assessments", null, null, 400));

        // --- assessments ---
        rows.add(row("assessment start: program id is not a UUID", "POST /api/v1/programs/{programId}/assessments", HttpMethod.POST,
                "/api/v1/programs/" + BAD_ID + "/assessments", null, null, 400));
        rows.add(row("assessment: id is not a UUID", "GET /api/v1/assessments/{id}", HttpMethod.GET, "/api/v1/assessments/" + BAD_ID, null, null, 400));
        rows.add(row("assessment usage: id is not a UUID", "GET /api/v1/assessments/{id}/usage", HttpMethod.GET,
                "/api/v1/assessments/" + BAD_ID + "/usage", null, null, 400));
        rows.add(row("assessment usage: unknown id", "GET /api/v1/assessments/{id}/usage", HttpMethod.GET,
                "/api/v1/assessments/" + ID + "/usage", null, null, 404));
        rows.add(row("assessment mappings: id is not a UUID", "GET /api/v1/assessments/{id}/mappings", HttpMethod.GET,
                "/api/v1/assessments/" + BAD_ID + "/mappings", null, null, 400));
        rows.add(row("assessment mappings: jurisdiction is not two capital letters", "GET /api/v1/assessments/{id}/mappings", HttpMethod.GET,
                "/api/v1/assessments/" + ID + "/mappings?jurisdiction=" + "x".repeat(5000), null, null, 400));

        // --- rule versions ---
        Map<String, Object> rule = Map.of("jurisdiction", "CA", "licenseType", "L", "label", "Demo rule set",
                "sourceUrl", "https://example.test/rule", "effectiveFrom", "2026-01-01", "text", "Rule text");
        String ruleVersions = "/api/v1/rule-versions";
        bodies.post("rule versions create: text missing", "POST /api/v1/rule-versions", ruleVersions, without(rule, "text"));
        bodies.post("rule versions create: oversized text", "POST /api/v1/rule-versions", ruleVersions, with(rule, "text", "t".repeat(500_001)));
        bodies.post("rule versions create: control character in text", "POST /api/v1/rule-versions", ruleVersions, with(rule, "text", "a\u0000b"));
        bodies.post("rule versions create: oversized label", "POST /api/v1/rule-versions", ruleVersions, with(rule, "label", "l".repeat(201)));
        bodies.post("rule versions create: source URL is not http", "POST /api/v1/rule-versions", ruleVersions, with(rule, "sourceUrl", "javascript:alert(1)"));
        bodies.post("rule versions create: date does not exist", "POST /api/v1/rule-versions", ruleVersions, with(rule, "effectiveFrom", "2026-13-45"));
        bodies.post("rule versions create: superseded id is not a UUID", "POST /api/v1/rule-versions", ruleVersions, with(rule, "supersedesId", BAD_ID));
        bodies.post("rule versions create: unknown jurisdiction code", "POST /api/v1/rule-versions", ruleVersions, with(rule, "jurisdiction", "ZZ"));
        rows.add(row("rule versions list: hostile query parameter is ignored", "GET /api/v1/rule-versions", HttpMethod.GET,
                "/api/v1/rule-versions?q=%00%ff", null, null, 200));
        rows.add(row("rule version: id is not a UUID", "GET /api/v1/rule-versions/{id}", HttpMethod.GET, "/api/v1/rule-versions/" + BAD_ID, null, null, 400));
        rows.add(row("requirements: id is not a UUID", "GET /api/v1/rule-versions/{id}/requirements", HttpMethod.GET,
                "/api/v1/rule-versions/" + BAD_ID + "/requirements", null, null, 400));
        Map<String, Object> requirement = Map.of("ref", "R1", "title", "Title", "text", "Text", "type", "SUBJECT");
        String requirements = "/api/v1/rule-versions/" + RULE + "/requirements";
        String replace = "PUT /api/v1/rule-versions/{id}/requirements";
        rows.add(row("requirements replace: id is not a UUID", replace, HttpMethod.PUT,
                "/api/v1/rule-versions/" + BAD_ID + "/requirements", json(Map.of("requirements", List.of(requirement))), JSON, 400));
        rows.add(rawJson("requirements replace: body is not an object", replace, HttpMethod.PUT, requirements, "\"text\"", 400));
        rows.add(rawJson("requirements replace: list missing", replace, HttpMethod.PUT, requirements, "{}", 400));
        rows.add(rawJson("requirements replace: list is a string", replace, HttpMethod.PUT, requirements, "{\"requirements\":\"none\"}", 400));
        rows.add(rawJson("requirements replace: entry is not an object", replace, HttpMethod.PUT, requirements, "{\"requirements\":[1,\"x\",null]}", 400));
        rows.add(rawJson("requirements replace: unknown type", replace, HttpMethod.PUT, requirements,
                json(Map.of("requirements", List.of(with(requirement, "type", "VIBES")))), 400));
        rows.add(rawJson("requirements replace: control character in title", replace, HttpMethod.PUT, requirements,
                json(Map.of("requirements", List.of(with(requirement, "title", "a\u0000b")))), 400));
        rows.add(rawJson("requirements replace: numeric value is not finite", replace, HttpMethod.PUT, requirements,
                "{\"requirements\":[{\"ref\":\"R1\",\"title\":\"T\",\"text\":\"X\",\"type\":\"NUMERIC\","
                        + "\"numeric_field\":\"TOTAL_CREDITS\",\"numeric_operator\":\">=\",\"numeric_value\":1e400}]}", 400));
        rows.add(rawJson("requirements replace: oversized text", replace, HttpMethod.PUT, requirements,
                json(Map.of("requirements", List.of(with(requirement, "text", "x".repeat(300_000))))), 400));
        rows.add(row("requirements approve: id is not a UUID", "POST /api/v1/rule-versions/{id}/requirements/approve", HttpMethod.POST,
                "/api/v1/rule-versions/" + BAD_ID + "/requirements/approve", null, null, 400));

        // --- reviews ---
        Map<String, Object> review = Map.of("kind", "ACCEPTED", "note", "Looks right");
        String reviews = "/api/v1/mappings/" + MAPPING + "/reviews";
        String postReview = "POST /api/v1/mappings/{id}/reviews";
        rows.add(row("review history: mapping id is not a UUID", "GET /api/v1/mappings/{id}/reviews", HttpMethod.GET,
                "/api/v1/mappings/" + BAD_ID + "/reviews", null, null, 400));
        rows.add(row("review: mapping id is not a UUID", postReview, HttpMethod.POST, "/api/v1/mappings/" + BAD_ID + "/reviews",
                json(review), JSON, 400));
        bodies.post("review: kind missing", postReview, reviews, without(review, "kind"));
        bodies.post("review: unknown kind", postReview, reviews, with(review, "kind", "APPROVED_BY_VIBES"));
        bodies.post("review: oversized note", postReview, reviews, with(review, "note", "n".repeat(4001)));
        bodies.post("review: control character in note", postReview, reviews, with(review, "note", "a\u0000b"));

        // --- determinations ---
        Map<String, Object> sign = Map.of("jurisdiction", "CA", "outcome", "MEETS");
        String determinations = "/api/v1/programs/" + ID + "/determinations";
        String postDetermination = "POST /api/v1/programs/{programId}/determinations";
        rows.add(signerRow("determination: program id is not a UUID", postDetermination, HttpMethod.POST,
                "/api/v1/programs/" + BAD_ID + "/determinations", json(sign), JSON, 400));
        rows.add(signerRow("determination: outcome missing", postDetermination, HttpMethod.POST, determinations, json(without(sign, "outcome")), JSON, 400));
        rows.add(signerRow("determination: unknown outcome", postDetermination, HttpMethod.POST, determinations, json(with(sign, "outcome", "COMPLIANT")), JSON, 400));
        rows.add(signerRow("determination: jurisdiction is not two capital letters", postDetermination, HttpMethod.POST, determinations,
                json(with(sign, "jurisdiction", "california")), JSON, 400));
        rows.add(signerRow("determination: body missing", postDetermination, HttpMethod.POST, determinations, null, null, 400));
        rows.add(row("determinations list: program id is not a UUID", "GET /api/v1/programs/{programId}/determinations", HttpMethod.GET,
                "/api/v1/programs/" + BAD_ID + "/determinations", null, null, 400));

        // --- determination register (F8) ---
        for (String path : List.of("/api/v1/determinations/register", "/api/v1/determinations/register.csv")) {
            String endpoint = "GET " + path;
            String kind = path.endsWith(".csv") ? "register CSV" : "register";
            rows.add(row(kind + ": program id is not a UUID", endpoint, HttpMethod.GET, path + "?programId=" + BAD_ID, null, null, 400));
            rows.add(row(kind + ": unknown outcome", endpoint, HttpMethod.GET, path + "?outcome=COMPLIANT", null, null, 400));
            rows.add(row(kind + ": jurisdiction is not two capital letters", endpoint, HttpMethod.GET, path + "?jurisdiction=california", null, null, 400));
            rows.add(row(kind + ": control character in jurisdiction", endpoint, HttpMethod.GET, path + "?jurisdiction=C%00", null, null, 400));
            rows.add(row(kind + ": current is not a boolean", endpoint, HttpMethod.GET, path + "?current=maybe", null, null, 400));
        }
        rows.add(row("determination lists: program id is not a UUID", "GET /api/v1/programs/{programId}/determination-lists", HttpMethod.GET,
                "/api/v1/programs/" + BAD_ID + "/determination-lists", null, null, 400));
        rows.add(row("determination lists: unknown program", "GET /api/v1/programs/{programId}/determination-lists", HttpMethod.GET,
                "/api/v1/programs/" + ID + "/determination-lists", null, null, 404));

        // --- evidence requests and responses ---
        Map<String, Object> response = Map.of("documentId", DOCUMENT, "pageNumber", 1, "quote", "Total credits: 60", "comment", "Sent");
        String evidence = "/api/v1/mappings/" + REQUESTED_MAPPING + "/evidence-responses";
        String postResponse = "POST /api/v1/mappings/{id}/evidence-responses";
        rows.add(row("evidence response: mapping id is not a UUID", postResponse, HttpMethod.POST,
                "/api/v1/mappings/" + BAD_ID + "/evidence-responses", json(response), JSON, 400));
        bodies.post("evidence response: quote missing", postResponse, evidence, without(response, "quote"));
        bodies.post("evidence response: document id is not a UUID", postResponse, evidence, with(response, "documentId", BAD_ID));
        bodies.post("evidence response: page number zero", postResponse, evidence, with(response, "pageNumber", 0));
        bodies.post("evidence response: oversized comment", postResponse, evidence, with(response, "comment", "c".repeat(4001)));
        bodies.post("evidence response: control character in comment", postResponse, evidence, with(response, "comment", "a\u0000b"));
        rows.add(row("evidence responses list: mapping id is not a UUID", "GET /api/v1/mappings/{id}/evidence-responses",
                HttpMethod.GET, "/api/v1/mappings/" + BAD_ID + "/evidence-responses", null, null, 400));
        rows.add(row("evidence requests: program id is not a UUID", "GET /api/v1/programs/{programId}/evidence-requests",
                HttpMethod.GET, "/api/v1/programs/" + BAD_ID + "/evidence-requests", null, null, 400));
        rows.add(row("evidence requests: unknown status", "GET /api/v1/programs/{programId}/evidence-requests",
                HttpMethod.GET, "/api/v1/programs/" + PROGRAM + "/evidence-requests?status=SOMETIMES", null, null, 400));

        // --- rule version diff ---
        rows.add(row("rule version diff: id is not a UUID", "GET /api/v1/rule-versions/{id}/diff", HttpMethod.GET,
                "/api/v1/rule-versions/" + BAD_ID + "/diff", null, null, 400));
        rows.add(row("rule version diff: compared version is not a UUID", "GET /api/v1/rule-versions/{id}/diff", HttpMethod.GET,
                "/api/v1/rule-versions/" + RULE + "/diff?against=" + BAD_ID, null, null, 400));

        // --- search ---
        String search = "GET /api/v1/programs/{programId}/search";
        String searchUri = "/api/v1/programs/" + PROGRAM + "/search";
        rows.add(row("search: program id is not a UUID", search, HttpMethod.GET, "/api/v1/programs/" + BAD_ID + "/search?q=ethics", null, null, 400));
        rows.add(row("search: query missing", search, HttpMethod.GET, searchUri, null, null, 400));
        rows.add(row("search: query blank", search, HttpMethod.GET, searchUri + "?q=%20%20", null, null, 400));
        rows.add(row("search: oversized query", search, HttpMethod.GET, searchUri + "?q=" + "q".repeat(5000), null, null, 400));
        rows.add(row("search: control character in query", search, HttpMethod.GET, searchUri + "?q=a%00b", null, null, 400));
        rows.add(row("search: limit below minimum", search, HttpMethod.GET, searchUri + "?q=ethics&limit=0", null, null, 400));
        rows.add(row("search: limit is not a number", search, HttpMethod.GET, searchUri + "?q=ethics&limit=many", null, null, 400));
        rows.add(row("search: unknown document kind", search, HttpMethod.GET, searchUri + "?q=ethics&kind=NOVEL", null, null, 400));
        rows.add(row("search: course version id is not a UUID", search, HttpMethod.GET, searchUri + "?q=ethics&courseVersionId=" + BAD_ID, null, null, 400));

        // --- change events ---
        rows.add(row("change events list: hostile query parameter is ignored", "GET /api/v1/change-events", HttpMethod.GET,
                "/api/v1/change-events?q=%00%ff", null, null, 200));
        rows.add(row("change event rerun: id is not a UUID", "POST /api/v1/change-events/{id}/rerun", HttpMethod.POST,
                "/api/v1/change-events/" + BAD_ID + "/rerun", null, null, 400));

        // --- work queue (no input beyond the session) ---
        rows.add(row("work queue: hostile query parameter is ignored", "GET /api/v1/work-queue", HttpMethod.GET,
                "/api/v1/work-queue?q=%00%ff&role=ADMIN", null, null, 200));
        rows.add(anonymousRow("work queue: no session", "GET /api/v1/work-queue", HttpMethod.GET, "/api/v1/work-queue", 401));
        rows.add(row("work queue count: hostile query parameter is ignored", "GET /api/v1/work-queue/count", HttpMethod.GET,
                "/api/v1/work-queue/count?q=%00%ff&role=ADMIN", null, null, 200));
        rows.add(anonymousRow("work queue count: no session", "GET /api/v1/work-queue/count", HttpMethod.GET,
                "/api/v1/work-queue/count", 401));

        // --- rule sources ---
        Map<String, Object> ruleSource = Map.of("jurisdictionCode", "CA", "licenseType", "L", "label", "Demo rule source",
                "url", "https://rules.example.test/rules.pdf", "checkIntervalHours", 24);
        String ruleSources = "/api/v1/rule-sources";
        String postRuleSource = "POST /api/v1/rule-sources";
        bodies.post("rule sources create: jurisdiction is not two capital letters", postRuleSource, ruleSources, with(ruleSource, "jurisdictionCode", "california"));
        bodies.post("rule sources create: unknown jurisdiction code", postRuleSource, ruleSources, with(ruleSource, "jurisdictionCode", "ZZ"));
        bodies.post("rule sources create: license type missing", postRuleSource, ruleSources, without(ruleSource, "licenseType"));
        bodies.post("rule sources create: oversized label", postRuleSource, ruleSources, with(ruleSource, "label", "l".repeat(151)));
        bodies.post("rule sources create: control character in label", postRuleSource, ruleSources, with(ruleSource, "label", "a\u0000b"));
        bodies.post("rule sources create: URL is not http", postRuleSource, ruleSources, with(ruleSource, "url", "ftp://rules.example.test/rules.pdf"));
        bodies.post("rule sources create: URL contains a space", postRuleSource, ruleSources, with(ruleSource, "url", "https://rules.example.test/a b"));
        bodies.post("rule sources create: URL is a loopback address", postRuleSource, ruleSources, with(ruleSource, "url", "http://127.0.0.1/rules"));
        bodies.post("rule sources create: URL is a private address", postRuleSource, ruleSources, with(ruleSource, "url", "http://10.0.0.1/rules"));
        bodies.post("rule sources create: URL is a link-local address", postRuleSource, ruleSources, with(ruleSource, "url", "http://169.254.1.1/rules"));
        bodies.post("rule sources create: URL is an IPv6 loopback address", postRuleSource, ruleSources, with(ruleSource, "url", "http://[::1]/rules"));
        bodies.post("rule sources create: URL host does not resolve", postRuleSource, ruleSources, with(ruleSource, "url", "https://nowhere.example.test/rules"));
        bodies.post("rule sources create: oversized URL", postRuleSource, ruleSources, with(ruleSource, "url", "https://rules.example.test/" + "u".repeat(2000)));
        bodies.post("rule sources create: interval below one hour", postRuleSource, ruleSources, with(ruleSource, "checkIntervalHours", 0));
        bodies.post("rule sources create: interval above thirty days", postRuleSource, ruleSources, with(ruleSource, "checkIntervalHours", 721));
        bodies.post("rule sources create: interval is not a number", postRuleSource, ruleSources, with(ruleSource, "checkIntervalHours", "daily"));
        rows.add(row("rule sources list: hostile query parameter is ignored", "GET /api/v1/rule-sources", HttpMethod.GET,
                ruleSources + "?q=%00%ff", null, null, 200));
        String checksEndpoint = "GET /api/v1/rule-sources/{id}/checks";
        rows.add(row("rule source checks: id is not a UUID", checksEndpoint, HttpMethod.GET, ruleSources + "/" + BAD_ID + "/checks", null, null, 400));
        rows.add(row("rule source checks: limit below minimum", checksEndpoint, HttpMethod.GET, ruleSources + "/" + ID + "/checks?limit=0", null, null, 400));
        rows.add(row("rule source checks: limit above maximum", checksEndpoint, HttpMethod.GET, ruleSources + "/" + ID + "/checks?limit=201", null, null, 400));
        rows.add(row("rule source checks: limit is not a number", checksEndpoint, HttpMethod.GET, ruleSources + "/" + ID + "/checks?limit=many", null, null, 400));
        rows.add(row("rule source check: id is not a UUID", "POST /api/v1/rule-sources/{id}/check", HttpMethod.POST,
                ruleSources + "/" + BAD_ID + "/check", null, null, 400));
        String putActive = "PUT /api/v1/rule-sources/{id}/active";
        rows.add(row("rule source active: id is not a UUID", putActive, HttpMethod.PUT, ruleSources + "/" + BAD_ID + "/active",
                json(Map.of("active", true)), JSON, 400));
        rows.add(rawJson("rule source active: flag missing", putActive, HttpMethod.PUT, ruleSources + "/" + ID + "/active", "{}", 400));
        rows.add(rawJson("rule source active: flag is not a boolean", putActive, HttpMethod.PUT, ruleSources + "/" + ID + "/active",
                "{\"active\":\"maybe\"}", 400));
        rows.add(rawJson("rule source active: body is not JSON", putActive, HttpMethod.PUT, ruleSources + "/" + ID + "/active", "{not json", 400));

        // --- reports ---
        rows.add(row("report request: determination id is not a UUID", "POST /api/v1/determinations/{id}/report", HttpMethod.POST,
                "/api/v1/determinations/" + BAD_ID + "/report", null, null, 400));
        rows.add(row("report download: id is not a UUID", "GET /api/v1/reports/{id}", HttpMethod.GET, "/api/v1/reports/" + BAD_ID, null, null, 400));
        return rows;
    }

    /** Bodies built from a valid base with one field spoiled; all are JSON POSTs expected to answer 400. */
    private record BodyRows(List<Row> rows) {
        void post(String name, String endpoint, String uri, Map<String, Object> body) {
            rows.add(rawJson(name, endpoint, HttpMethod.POST, uri, json(body), 400));
        }
    }

    private static Row row(String name, String endpoint, HttpMethod method, String uri, String body, String contentType, int status) {
        return new Row(name, endpoint, method, uri, body, contentType, status, As.ADMIN);
    }

    private static Row signerRow(String name, String endpoint, HttpMethod method, String uri, String body, String contentType, int status) {
        return new Row(name, endpoint, method, uri, body, contentType, status, As.SIGNER);
    }

    private static Row anonymousRow(String name, String endpoint, HttpMethod method, String uri, int status) {
        return new Row(name, endpoint, method, uri, null, null, status, As.ANONYMOUS);
    }

    private static Row rawJson(String name, String endpoint, HttpMethod method, String uri, String body, int status) {
        return row(name, endpoint, method, uri, body, JSON, status);
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(base);
        copy.put(key, value);
        return copy;
    }

    private static Map<String, Object> without(Map<String, Object> base, String key) {
        Map<String, Object> copy = new LinkedHashMap<>(base);
        copy.remove(key);
        return copy;
    }

    private static String json(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
