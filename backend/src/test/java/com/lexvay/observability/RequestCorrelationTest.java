package com.lexvay.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestCsrf;
import com.lexvay.common.TestTenants;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Request ids: accepted format, generation, echo, the logging context, and the problem detail property. */
class RequestCorrelationTest extends AbstractDatabaseTest {

    private static final String PROBE = "/api/v1/test-support/mdc";

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;

    private JsonNode body(MvcTestResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void generatesAUuidWhenTheClientSendsNone() throws Exception {
        var t = tenants.create("corr");
        var result = mvc.get().uri(PROBE).with(user(t.reviewer())).exchange();
        String header = result.getResponse().getHeader("X-Request-Id");
        assertThat(UUID.fromString(header)).isNotNull();
        assertThat(body(result).get("requestId").asString()).isEqualTo(header);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefgh", "0123456789-ABCDEF-0123456789-abcdef", "A".repeat(64)})
    void echoesAClientIdOfEightToSixtyFourSafeCharacters(String supplied) throws Exception {
        var t = tenants.create("corr");
        var result = mvc.get().uri(PROBE).header("X-Request-Id", supplied).with(user(t.reviewer())).exchange();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo(supplied);
        assertThat(body(result).get("requestId").asString()).isEqualTo(supplied);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short-7", "A".repeat(65), "has space inside", "under_score_id", "semi;colon-1234", "café-12345678",
            "<script>-12345678"})
    void replacesAClientIdOutsideTheAcceptedFormat(String supplied) throws Exception {
        var t = tenants.create("corr");
        var result = mvc.get().uri(PROBE).header("X-Request-Id", supplied).with(user(t.reviewer())).exchange();
        String header = result.getResponse().getHeader("X-Request-Id");
        assertThat(header).isNotEqualTo(supplied);
        assertThat(UUID.fromString(header)).isNotNull();
        assertThat(body(result).get("requestId").asString()).isEqualTo(header);
    }

    @Test
    void theLoggingContextCarriesRequestTenantAndUser() throws Exception {
        var t = tenants.create("corr");
        var result = mvc.get().uri(PROBE).header("X-Request-Id", "req-context-1").with(user(t.reviewer())).exchange();
        JsonNode context = body(result);
        assertThat(context.get("requestId").asString()).isEqualTo("req-context-1");
        assertThat(context.get("tenantId").asString()).isEqualTo(t.tenantId().toString());
        assertThat(context.get("userId").asString()).isEqualTo(t.reviewer().id().toString());
        assertThat(context.has("jobId")).isFalse();
        assertThat(MDC.getCopyOfContextMap()).as("nothing leaks to the next request on this thread").isNullOrEmpty();
    }

    @Test
    void everyProblemDetailCarriesTheRequestId() throws Exception {
        var a = tenants.create("corr");
        var b = tenants.create("corr");
        UUID foreignId = UUID.randomUUID();

        assertProblem(mvc.get().uri("/api/v1/unknown-endpoint").header("X-Request-Id", "problem-404-route").with(user(a.reviewer())).exchange(),
                404, "problem-404-route");
        assertProblem(mvc.get().uri("/api/v1/assessments/" + foreignId).header("X-Request-Id", "problem-404-entity").with(user(a.reviewer())).exchange(),
                404, "problem-404-entity");
        assertProblem(mvc.get().uri("/api/v1/programs/not-a-uuid").header("X-Request-Id", "problem-400-bad-id").with(user(a.reviewer())).exchange(),
                400, "problem-400-bad-id");
        assertProblem(mvc.post().uri("/api/v1/change-events/" + foreignId + "/rerun").with(TestCsrf.csrf())
                        .header("X-Request-Id", "problem-403-role").with(user(b.contributor())).exchange(),
                403, "problem-403-role");
        assertProblem(mvc.get().uri("/api/v1/programs").header("X-Request-Id", "problem-401-anon").exchange(), 401, "problem-401-anon");
        assertProblem(mvc.get().uri("/api/v1/unknown-endpoint").with(user(a.reviewer())).exchange(), 404, null);
    }

    private void assertProblem(MvcTestResult result, int status, String expectedRequestId) throws Exception {
        assertThat(result).hasStatus(status);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        String header = result.getResponse().getHeader("X-Request-Id");
        assertThat(body(result).get("requestId").asString()).isEqualTo(header);
        if (expectedRequestId != null) {
            assertThat(header).isEqualTo(expectedRequestId);
        }
    }
}
