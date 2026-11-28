package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexvay.job.JobRepository;
import com.lexvay.observability.LexvayMetrics;
import com.lexvay.tenant.DataMode;
import com.lexvay.tenant.TenantRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class LlmGatewayTest {

    private static final LlmRequest REQUEST = new LlmRequest(LlmPurpose.MATCH_EVIDENCE, "MATCH:X-1", "system", "user", null, 100);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LexvayMetrics metrics = new LexvayMetrics(registry, mock(JobRepository.class));
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final SpendGate spendGate = mock(SpendGate.class);
    private final LlmRefusalAudit refusals = mock(LlmRefusalAudit.class);

    private LlmGateway gateway(LlmClient client) {
        return new LlmGateway(client, metrics, tenants, spendGate, refusals);
    }

    private double calls(String outcome) {
        var counter = registry.find("lexvay.llm.calls").tags("purpose", "MATCH_EVIDENCE", "model", "test-model", "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void aSuccessfulCallCountsTokensAndDuration() {
        LlmGateway gateway = gateway(new StubClient(new LlmResponse("{}", "test-model", 120, 30, 5), null, false, false));

        assertThat(gateway.complete(REQUEST).text()).isEqualTo("{}");

        assertThat(calls("success")).isEqualTo(1);
        assertThat(registry.get("lexvay.llm.tokens").tag("direction", "input").counter().count()).isEqualTo(120);
        assertThat(registry.get("lexvay.llm.tokens").tag("direction", "output").counter().count()).isEqualTo(30);
        assertThat(registry.get("lexvay.llm.duration").tag("purpose", "MATCH_EVIDENCE").timer().count()).isEqualTo(1);
    }

    @Test
    void aRefusalIsCountedAsRefusedAndRethrown() {
        LlmGateway gateway = gateway(new StubClient(null, new LlmRefusedException("declined"), false, false));

        assertThatThrownBy(() -> gateway.complete(REQUEST)).isInstanceOf(LlmRefusedException.class);

        assertThat(calls("refused")).isEqualTo(1);
        assertThat(calls("success")).isZero();
        assertThat(registry.find("lexvay.llm.tokens").counter()).isNull();
    }

    @Test
    void anyOtherFailureIsCountedAsErrorAndRethrown() {
        LlmGateway gateway = gateway(new StubClient(null, new IllegalStateException("network down"), false, false));

        assertThatThrownBy(() -> gateway.complete(REQUEST)).isInstanceOf(IllegalStateException.class);

        assertThat(calls("error")).isEqualTo(1);
        assertThat(registry.get("lexvay.llm.duration").tag("purpose", "MATCH_EVIDENCE").timer().count()).isEqualTo(1);
    }

    @Test
    void theModelIdIsTheClients() {
        assertThat(gateway(new StubClient(null, null, false, false)).modelId()).isEqualTo("test-model");
    }

    @Test
    void theScriptedModelNeverAnswersForALiveTenantAndTheRefusalIsAudited() {
        when(tenants.currentDataMode()).thenReturn(DataMode.LIVE);
        StubClient scripted = new StubClient(new LlmResponse("{}", "test-model", 1, 1, 1), null, false, true);
        LlmGateway gateway = gateway(scripted);

        assertThatThrownBy(() -> gateway.complete(REQUEST)).isInstanceOfSatisfying(LlmCallBlockedException.class, e -> {
            assertThat(e.reason()).isEqualTo(LlmCallBlockedException.Reason.NO_MODEL_FOR_REAL_DATA);
            assertThat(e).hasMessage("No model is configured for real data");
        });

        assertThat(scripted.calls).isZero();
        verify(refusals).record(eq(REQUEST), eq("test-model"), any(LlmCallBlockedException.class));
        verifyNoInteractions(spendGate);
        assertThat(calls("refused")).isEqualTo(1);
    }

    @Test
    void theScriptedModelStillAnswersForADemoTenant() {
        when(tenants.currentDataMode()).thenReturn(DataMode.DEMO);
        StubClient scripted = new StubClient(new LlmResponse("{}", "test-model", 1, 1, 1), null, false, true);

        assertThat(gateway(scripted).complete(REQUEST).text()).isEqualTo("{}");

        assertThat(scripted.calls).isEqualTo(1);
        verifyNoInteractions(spendGate, refusals);
    }

    @Test
    void aMeteredCallAsksTheSpendGateFirstAndAnAnswerOfNoStopsIt() {
        StubClient metered = new StubClient(new LlmResponse("{}", "test-model", 1, 1, 1), null, true, false);
        LlmCallBlockedException refusal = new LlmCallBlockedException(LlmCallBlockedException.Reason.OVER_BUDGET, "over");
        org.mockito.Mockito.doThrow(refusal).when(spendGate).check(REQUEST, "test-model");

        assertThatThrownBy(() -> gateway(metered).complete(REQUEST)).isSameAs(refusal);

        assertThat(metered.calls).isZero();
        verify(refusals).record(REQUEST, "test-model", refusal);
        verifyNoInteractions(tenants);
        assertThat(calls("refused")).isEqualTo(1);
    }

    @Test
    void aMeteredCallTheGateAllowsIsMadeOnceAndNeverReadsTheTenant() {
        StubClient metered = new StubClient(new LlmResponse("{}", "test-model", 1, 1, 1), null, true, false);

        assertThat(gateway(metered).complete(REQUEST).text()).isEqualTo("{}");

        assertThat(metered.calls).isEqualTo(1);
        verify(spendGate).check(REQUEST, "test-model");
        verifyNoInteractions(tenants, refusals);
    }

    private static final class StubClient implements LlmClient {
        private final LlmResponse response;
        private final RuntimeException failure;
        private final boolean metered;
        private final boolean scripted;
        int calls;

        StubClient(LlmResponse response, RuntimeException failure, boolean metered, boolean scripted) {
            this.response = response;
            this.failure = failure;
            this.metered = metered;
            this.scripted = scripted;
        }

        @Override
        public boolean metered() {
            return metered;
        }

        @Override
        public boolean scripted() {
            return scripted;
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            calls++;
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
