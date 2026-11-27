package com.lexvay.llm;

import com.lexvay.observability.LexvayMetrics;
import com.lexvay.observability.LexvayMetrics.LlmOutcome;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * What the pipeline calls to reach the model. Delegates to the configured {@link LlmClient} and reports
 * each call, including refusals and failures, to {@link LexvayMetrics}. Storing the raw input and output
 * stays with {@link LlmCallRecorder}.
 */
@Component
public class LlmGateway {

    private final LlmClient client;
    private final LexvayMetrics metrics;

    public LlmGateway(LlmClient client, LexvayMetrics metrics) {
        this.client = client;
        this.metrics = metrics;
    }

    public LlmResponse complete(LlmRequest request) {
        long started = System.nanoTime();
        try {
            LlmResponse response = client.complete(request);
            metrics.llmCall(request.purpose(), response.model(), LlmOutcome.SUCCESS, since(started),
                    response.inputTokens(), response.outputTokens());
            return response;
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

    private static Duration since(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }
}
