package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexvay.assessment.match.EvidenceMatcher;
import com.lexvay.job.JobRepository;
import com.lexvay.observability.LexvayMetrics;
import com.lexvay.tenant.DataMode;
import com.lexvay.tenant.TenantRepository;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The local inference adapter against an in-process server on the loopback interface: no network, no model. */
class OpenAiCompatibleLlmClientTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String MODEL = "local-test-model";
    static final String VALID_ANSWER = "{\"finding\":\"EVIDENCE_MISSING\",\"document_id\":null,\"page_number\":null,\"quote\":null,"
            + "\"rationale\":\"Nothing offered covers it.\"}";

    /** One request the server saw, and the reply it gives to the next one. */
    record Seen(String authorization, JsonNode body) {
    }

    record Reply(int status, String body, long delayMillis) {
        static Reply ok(String content, String usage) {
            return new Reply(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + JSON.writeValueAsString(content) + "}}]"
                    + (usage == null ? "" : ",\"usage\":" + usage) + "}", 0);
        }

        static Reply status(int status, String body) {
            return new Reply(status, body, 0);
        }
    }

    final List<Seen> seen = new CopyOnWriteArrayList<>();
    final Deque<Reply> replies = new ConcurrentLinkedDeque<>();
    final ExecutorService handlers = Executors.newCachedThreadPool();
    HttpServer server;
    LlmRequest request;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
        server.setExecutor(handlers);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.add(new Seen(exchange.getRequestHeaders().getFirst("Authorization"), JSON.readTree(body)));
            Reply reply = replies.poll();
            try {
                Thread.sleep(reply.delayMillis());
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException | IOException stopped) {
                // the client gave up (a timeout test) or the server was stopped
            } finally {
                exchange.close();
            }
        });
        server.start();
        request = new LlmRequest(LlmPurpose.MATCH_EVIDENCE, "MATCH:X-1", "You compare text.", "Requirement and passages.",
                JSON.readTree("{\"type\":\"object\"}"), 500);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        handlers.shutdownNow();
    }

    String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/v1";
    }

    OpenAiCompatibleLlmClient client(String apiKey, Duration timeout) {
        return new OpenAiCompatibleLlmClient(baseUrl(), MODEL, apiKey, timeout, List.of("localhost", "127.0.0.1", "::1"));
    }

    @Test
    void sendsTheSchemaAsAStrictJsonSchemaResponseFormatAndReadsTheAnswerAndTheReportedUsage() {
        replies.add(Reply.ok(VALID_ANSWER, "{\"prompt_tokens\":321,\"completion_tokens\":45,\"total_tokens\":366}"));

        LlmResponse response = client("", Duration.ofSeconds(5)).complete(request);

        assertThat(response.text()).isEqualTo(VALID_ANSWER);
        assertThat(response.model()).isEqualTo(MODEL);
        assertThat(response.inputTokens()).isEqualTo(321);
        assertThat(response.outputTokens()).isEqualTo(45);
        assertThat(response.tokensEstimated()).isFalse();
        assertThat(seen).hasSize(1);
        JsonNode body = seen.get(0).body();
        assertThat(body.path("model").asString()).isEqualTo(MODEL);
        assertThat(body.path("max_tokens").asInt()).isEqualTo(500);
        assertThat(body.path("messages").path(0).path("role").asString()).isEqualTo("system");
        assertThat(body.path("messages").path(0).path("content").asString()).isEqualTo("You compare text.");
        assertThat(body.path("messages").path(1).path("content").asString()).isEqualTo("Requirement and passages.");
        JsonNode format = body.path("response_format");
        assertThat(format.path("type").asString()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("name").asString()).isEqualTo("match_evidence");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("schema")).isEqualTo(request.outputSchema());
        assertThat(seen.get(0).authorization()).as("no key configured, none sent").isNull();
    }

    @Test
    void sendsTheKeyAsABearerTokenOnlyWhenOneIsConfigured() {
        replies.add(Reply.ok(VALID_ANSWER, null));

        client("secret-token", Duration.ofSeconds(5)).complete(request);

        assertThat(seen.get(0).authorization()).isEqualTo("Bearer secret-token");
    }

    @Test
    void aServerThatRejectsResponseFormatIsAskedOnceMoreWithTheSchemaInTheSystemMessage() {
        replies.add(Reply.status(400, "{\"error\":{\"message\":\"Unsupported field: response_format\"}}"));
        replies.add(Reply.ok(VALID_ANSWER, "{\"prompt_tokens\":10,\"completion_tokens\":5}"));

        LlmResponse response = client("", Duration.ofSeconds(5)).complete(request);

        assertThat(response.text()).isEqualTo(VALID_ANSWER);
        assertThat(seen).hasSize(2);
        assertThat(seen.get(0).body().has("response_format")).isTrue();
        JsonNode retry = seen.get(1).body();
        assertThat(retry.has("response_format")).isFalse();
        assertThat(retry.path("messages").path(0).path("content").asString())
                .startsWith("You compare text.").contains("JSON schema").contains("{\"type\":\"object\"}");
        assertThat(retry.path("messages").path(1).path("content").asString()).isEqualTo("Requirement and passages.");
    }

    @Test
    void aBadRequestThatDoesNotNameResponseFormatIsNotRetried() {
        replies.add(Reply.status(400, "{\"error\":{\"message\":\"context length exceeded\"}}"));

        assertThatThrownBy(() -> client("", Duration.ofSeconds(5)).complete(request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTP 400").hasMessageContaining("context length exceeded");
        assertThat(seen).hasSize(1);
    }

    @Test
    void aSecondRejectionAfterTheFallbackFailsTheCall() {
        replies.add(Reply.status(400, "response_format is unknown"));
        replies.add(Reply.status(400, "response_format is unknown"));

        assertThatThrownBy(() -> client("", Duration.ofSeconds(5)).complete(request)).hasMessageContaining("HTTP 400");
        assertThat(seen).hasSize(2);
    }

    @Test
    void aRedirectIsNeverFollowed() {
        replies.add(Reply.status(302, ""));

        assertThatThrownBy(() -> client("", Duration.ofSeconds(5)).complete(request)).hasMessageContaining("HTTP 302");
        assertThat(seen).hasSize(1);
    }

    @Test
    void anAnswerThatIsNotJsonIsReturnedAsItIsAndTheSameValidatorRejectsItAsForAnyProvider() {
        replies.add(Reply.ok("Sure! Here is my answer: it is probably found.", "{\"prompt_tokens\":10,\"completion_tokens\":5}"));

        LlmResponse response = client("", Duration.ofSeconds(5)).complete(request);

        JsonSchemaValidator.Result result = new JsonSchemaValidator(JSON, EvidenceMatcher.SCHEMA).validate(response.text());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).singleElement().asString().startsWith("Output is not valid JSON");
    }

    @Test
    void anAnswerThatBreaksTheSchemaIsReturnedAsItIsAndTheSameValidatorRejectsIt() {
        replies.add(Reply.ok("{\"finding\":\"COMPLIANT\"}", "{\"prompt_tokens\":10,\"completion_tokens\":5}"));

        LlmResponse response = client("", Duration.ofSeconds(5)).complete(request);

        assertThat(new JsonSchemaValidator(JSON, EvidenceMatcher.SCHEMA).validate(response.text()).valid()).isFalse();
    }

    @Test
    void aServerThatDoesNotAnswerInTimeFailsTheCallNamingTheHost() {
        replies.add(new Reply(200, "{}", 3_000));

        assertThatThrownBy(() -> client("", Duration.ofSeconds(1)).complete(request))
                .isInstanceOf(UncheckedIOException.class).hasMessageContaining("127.0.0.1").hasMessageContaining("did not answer within 1 s");
    }

    @Test
    void anAnswerWithoutUsageGetsEstimatedTokenCountsAndSaysSo() {
        replies.add(Reply.ok(VALID_ANSWER, null));

        LlmResponse response = client("", Duration.ofSeconds(5)).complete(request);

        assertThat(response.tokensEstimated()).isTrue();
        assertThat(response.inputTokens()).isEqualTo(LlmResponse.estimateInputTokens(request)).isEqualTo("Requirement and passages.".length() / 4);
        assertThat(response.outputTokens()).isEqualTo(VALID_ANSWER.length() / 4);
    }

    @Test
    void usageWithOnlyOneCountIsEstimatedAsAWholeRatherThanMixed() {
        replies.add(Reply.ok(VALID_ANSWER, "{\"prompt_tokens\":10}"));

        LlmResponse response = client("", Duration.ofSeconds(5)).complete(request);

        assertThat(response.tokensEstimated()).isTrue();
        assertThat(response.inputTokens()).isEqualTo(LlmResponse.estimateInputTokens(request));
    }

    @Test
    void aRefusalFromTheServerIsTheSameFailureAsTheClaudeApisRefusal() {
        replies.add(new Reply(200, "{\"choices\":[{\"message\":{\"content\":null,\"refusal\":\"I cannot help with that.\"}}]}", 0));

        assertThatThrownBy(() -> client("", Duration.ofSeconds(5)).complete(request))
                .isInstanceOf(LlmRefusedException.class).hasMessageContaining("I cannot help with that.");
    }

    @Test
    void anAnswerWithoutMessageContentFailsTheCall() {
        replies.add(Reply.status(200, "{\"choices\":[]}"));

        assertThatThrownBy(() -> client("", Duration.ofSeconds(5)).complete(request)).hasMessageContaining("returned no message content");
    }

    @Test
    void aUrlWithATrailingSlashReachesTheSameEndpoint() {
        replies.add(Reply.ok(VALID_ANSWER, null));

        new OpenAiCompatibleLlmClient(baseUrl() + "/", MODEL, "", Duration.ofSeconds(5), List.of("127.0.0.1")).complete(request);

        assertThat(seen).hasSize(1);
    }

    @Test
    void aLiveTenantIsServedAndNeitherTheSpendGateNorTheTenantsModeIsConsulted() {
        replies.add(Reply.ok(VALID_ANSWER, "{\"prompt_tokens\":10,\"completion_tokens\":5}"));
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.currentDataMode()).thenReturn(DataMode.LIVE);
        SpendGate spendGate = mock(SpendGate.class);
        LlmRefusalAudit refusals = mock(LlmRefusalAudit.class);
        LexvayMetrics metrics = new LexvayMetrics(new SimpleMeterRegistry(), mock(JobRepository.class));
        LlmGateway gateway = new LlmGateway(client("", Duration.ofSeconds(5)), metrics, tenants, spendGate, refusals);

        assertThat(gateway.complete(request).text()).isEqualTo(VALID_ANSWER);

        assertThat(seen).hasSize(1);
        verifyNoInteractions(tenants, spendGate, refusals);
    }

    @Test
    void isNotMeteredNotScriptedAndNamesItsHostAndModel() {
        OpenAiCompatibleLlmClient client = client("", Duration.ofSeconds(5));

        assertThat(client.metered()).isFalse();
        assertThat(client.scripted()).isFalse();
        assertThat(client.modelId()).isEqualTo(MODEL);
        assertThat(client.endpointHost()).contains("127.0.0.1");
    }

    @Test
    void aHostOutsideTheAllowedListFailsConstructionNamingTheHostAndTheVariable() {
        assertThatThrownBy(() -> new OpenAiCompatibleLlmClient("https://models.example.org/v1", MODEL, "", Duration.ofSeconds(5),
                List.of("localhost", "127.0.0.1", "::1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("models.example.org").hasMessageContaining("LEXVAY_LLM_ALLOWED_HOSTS");
    }

    @Test
    void aRemoteHostListedExplicitlyIsAccepted() {
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient("https://Models.Example.org:8443/v1", MODEL, "", Duration.ofSeconds(5),
                List.of("models.example.org"));

        assertThat(client.endpointHost()).contains("models.example.org");
    }

    @Test
    void anIpv6LoopbackUrlMatchesTheBareAddressInTheList() {
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient("http://[::1]:8089/v1", MODEL, "", Duration.ofSeconds(5),
                List.of("localhost", "127.0.0.1", "::1"));

        assertThat(client.endpointHost()).contains("::1");
    }

    @Test
    void anAllowedNameThatOnlyEndsTheSameWayIsNotAMatch() {
        assertThatThrownBy(() -> new OpenAiCompatibleLlmClient("http://evil-localhost/v1", MODEL, "", Duration.ofSeconds(5), List.of("localhost")))
                .hasMessageContaining("evil-localhost");
    }

    @Test
    void theBaseUrlAndTheModelAreRequiredAndTheModelHasNoDefault() {
        List<String> hosts = List.of("localhost");
        assertThatThrownBy(() -> new OpenAiCompatibleLlmClient("", MODEL, "", Duration.ofSeconds(5), hosts))
                .hasMessageContaining("LEXVAY_LLM_BASE_URL must be set");
        assertThatThrownBy(() -> new OpenAiCompatibleLlmClient("http://localhost:8089/v1", " ", "", Duration.ofSeconds(5), hosts))
                .hasMessageContaining("LEXVAY_LLM_MODEL must be set");
        assertThatThrownBy(() -> new OpenAiCompatibleLlmClient("localhost:8089/v1", MODEL, "", Duration.ofSeconds(5), hosts))
                .hasMessageContaining("http or https");
    }
}
