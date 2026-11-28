package com.lexvay.llm;

import com.lexvay.llm.LlmCallBlockedException.Reason;
import com.lexvay.observability.LexvayMetrics;
import com.lexvay.observability.LexvayMetrics.LlmOutcome;
import com.lexvay.tenant.DataMode;
import com.lexvay.tenant.TenantRepository;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * What the pipeline calls to reach the model. Before delegating to the configured {@link LlmClient} it checks that the
 * call is allowed: the scripted model never answers for a LIVE tenant (D-561), and a metered provider needs the
 * {@link SpendGate}'s approval (D-562). A refused call is audited, nothing is sent, and the job fails. Each call,
 * including refusals and failures, is reported to {@link LexvayMetrics}. Storing the raw input and output stays with
 * {@link LlmCallRecorder}.
 */
@Component
public class LlmGateway {

    private final LlmClient client;
    private final LexvayMetrics metrics;
    private final TenantRepository tenants;
    private final SpendGate spendGate;
    private final LlmRefusalAudit refusals;

    public LlmGateway(LlmClient client, LexvayMetrics metrics, TenantRepository tenants, SpendGate spendGate,
                      LlmRefusalAudit refusals) {
        this.client = client;
        this.metrics = metrics;
        this.tenants = tenants;
        this.spendGate = spendGate;
        this.refusals = refusals;
    }

    public LlmResponse complete(LlmRequest request) {
        long started = System.nanoTime();
        try {
            admit(request);
            LlmResponse response = client.complete(request);
            metrics.llmCall(request.purpose(), response.model(), LlmOutcome.SUCCESS, since(started),
                    response.inputTokens(), response.outputTokens());
            return response;
        } catch (LlmCallBlockedException e) {
            refusals.record(request, client.modelId(), e);
            metrics.llmCall(request.purpose(), client.modelId(), LlmOutcome.REFUSED, since(started), null, null);
            throw e;
        } catch (LlmRefusedException e) {
            metrics.llmCall(request.purpose(), client.modelId(), LlmOutcome.REFUSED, since(started), null, null);
            throw e;
        } catch (RuntimeException e) {
            metrics.llmCall(request.purpose(), client.modelId(), LlmOutcome.ERROR, since(started), null, null);
            throw e;
        }
    }

    public String modelId() {
        return client.modelId();
    }

    /** The tenant's mode is read only when the client is scripted, so the call to a real model costs no extra query. */
    private void admit(LlmRequest request) {
        if (client.scripted() && tenants.currentDataMode() == DataMode.LIVE) {
            throw new LlmCallBlockedException(Reason.NO_MODEL_FOR_REAL_DATA, "No model is configured for real data");
        }
        if (client.metered()) {
            spendGate.check(request, client.modelId());
        }
    }

    private static Duration since(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }
}
