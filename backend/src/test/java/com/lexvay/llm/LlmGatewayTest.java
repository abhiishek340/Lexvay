package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.lexvay.job.JobRepository;
import com.lexvay.observability.LexvayMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class LlmGatewayTest {

    private static final LlmRequest REQUEST = new LlmRequest(LlmPurpose.MATCH_EVIDENCE, "MATCH:X-1", "system", "user", null, 100);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LexvayMetrics metrics = new LexvayMetrics(registry, mock(JobRepository.class));

    private double calls(String outcome) {
        var counter = registry.find("lexvay.llm.calls").tags("purpose", "MATCH_EVIDENCE", "model", "test-model", "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void aSuccessfulCallCountsTokensAndDuration() {
        LlmGateway gateway = new LlmGateway(new StubClient(new LlmResponse("{}", "test-model", 120, 30, 5), null), metrics);

        assertThat(gateway.complete(REQUEST).text()).isEqualTo("{}");

        assertThat(calls("success")).isEqualTo(1);
        assertThat(registry.get("lexvay.llm.tokens").tag("direction", "input").counter().count()).isEqualTo(120);
        assertThat(registry.get("lexvay.llm.tokens").tag("direction", "output").counter().count()).isEqualTo(30);
        assertThat(registry.get("lexvay.llm.duration").tag("purpose", "MATCH_EVIDENCE").timer().count()).isEqualTo(1);
    }

    @Test
    void aRefusalIsCountedAsRefusedAndRethrown() {
        LlmGateway gateway = new LlmGateway(new StubClient(null, new LlmRefusedException("declined")), metrics);

        assertThatThrownBy(() -> gateway.complete(REQUEST)).isInstanceOf(LlmRefusedException.class);

        assertThat(calls("refused")).isEqualTo(1);
        assertThat(calls("success")).isZero();
        assertThat(registry.find("lexvay.llm.tokens").counter()).isNull();
    }

    @Test
    void anyOtherFailureIsCountedAsErrorAndRethrown() {
        LlmGateway gateway = new LlmGateway(new StubClient(null, new IllegalStateException("network down")), metrics);

        assertThatThrownBy(() -> gateway.complete(REQUEST)).isInstanceOf(IllegalStateException.class);

        assertThat(calls("error")).isEqualTo(1);
        assertThat(registry.get("lexvay.llm.duration").tag("purpose", "MATCH_EVIDENCE").timer().count()).isEqualTo(1);
    }

    @Test
    void theModelIdIsTheClients() {
        assertThat(new LlmGateway(new StubClient(null, null), metrics).modelId()).isEqualTo("test-model");
    }

    private record StubClient(LlmResponse response, RuntimeException failure) implements LlmClient {
        @Override
        public LlmResponse complete(LlmRequest request) {
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        public String modelId() {
            return "test-model";
        }
    }
}
