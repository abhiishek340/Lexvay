package com.lexvay.perf;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.common.CountingDataSource;
import com.lexvay.review.ReviewFixtures;
import com.lexvay.rule.source.FakeHostResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * One connection per read request: every read endpoint, however many statements it runs, checks out exactly
 * one connection (and so binds the tenant once). Guards the read-only transactions on the service methods.
 */
@Import(CountingDataSource.Config.class)
class ReadPathConnectionsTest extends AbstractAssessmentTest {

    @Autowired DataSource dataSource;

    @Test
    void everyReadEndpointUsesExactlyOneConnection() throws Exception {
        var s = new ReviewFixtures(mvc, json, fx).californiaScenario(t, "conn " + UUID.randomUUID().toString().substring(0, 6));
        JsonNode mapping = fx.mappingFor(fx.mappings(t, s.assessmentId(), "CA"), "CA-3");
        String document = mapping.get("evidence").get("documentId").asString();
        String program = "/api/v1/programs/" + s.programId();

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("program", program);
        endpoints.put("programs", "/api/v1/programs");
        endpoints.put("courses", program + "/courses");
        endpoints.put("facts", program + "/facts");
        endpoints.put("determinations", program + "/determinations");
        endpoints.put("overview", program + "/overview");
        endpoints.put("assessments of a program", program + "/assessments");
        endpoints.put("assessment", "/api/v1/assessments/" + s.assessmentId());
        endpoints.put("mappings", "/api/v1/assessments/" + s.assessmentId() + "/mappings");
        endpoints.put("mappings of a jurisdiction", "/api/v1/assessments/" + s.assessmentId() + "/mappings?jurisdiction=CA");
        endpoints.put("review history", "/api/v1/mappings/" + s.foundMapping() + "/reviews");
        endpoints.put("evidence responses", "/api/v1/mappings/" + s.foundMapping() + "/evidence-responses");
        endpoints.put("evidence requests", program + "/evidence-requests?status=ALL");
        endpoints.put("search", program + "/search?q=ethics");
        endpoints.put("document", "/api/v1/documents/" + document);
        endpoints.put("document page", "/api/v1/documents/" + document + "/pages/1");
        endpoints.put("document file", "/api/v1/documents/" + document + "/file");
        endpoints.put("rule versions", "/api/v1/rule-versions");
        endpoints.put("rule version diff", "/api/v1/rule-versions/" + s.ruleVersionId() + "/diff?against=" + s.ruleVersionId());
        endpoints.put("requirements", "/api/v1/rule-versions/" + s.ruleVersionId() + "/requirements");
        endpoints.put("change events", "/api/v1/change-events");
        endpoints.put("audit", "/api/v1/audit");
        endpoints.put("determination register", "/api/v1/determinations/register?current=false");
        endpoints.put("determination register of a program", "/api/v1/determinations/register?programId=" + s.programId() + "&jurisdiction=CA&outcome=MEETS");
        endpoints.put("determination lists", program + "/determination-lists");
        endpoints.put("work queue", "/api/v1/work-queue");
        endpoints.put("work queue count", "/api/v1/work-queue/count");
        var source = mvc.post().uri("/api/v1/rule-sources").with(user(t.admin())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jurisdictionCode", "CA", "licenseType", AssessmentFixtures.LICENSE,
                        "label", "Demo rule source", "url", "https://" + FakeHostResolver.PUBLIC_HOST + "/rules.pdf", "checkIntervalHours", 24)))
                .exchange();
        assertThat(source).hasStatus(201);
        endpoints.put("rule sources", "/api/v1/rule-sources");
        endpoints.put("rule source checks", "/api/v1/rule-sources/" + fx.read(source).get("id").asString() + "/checks");

        CountingDataSource counter = (CountingDataSource) dataSource;
        Map<String, Long> used = new LinkedHashMap<>();
        for (var endpoint : endpoints.entrySet()) {
            long before = counter.checkouts();
            assertThat(mvc.get().uri(endpoint.getValue()).with(user(t.reviewer())).exchange()).as(endpoint.getKey()).hasStatus(200);
            used.put(endpoint.getKey(), counter.checkouts() - before);
        }
        assertThat(used).allSatisfy((name, connections) -> assertThat(connections).as(name).isEqualTo(1));
    }
}
