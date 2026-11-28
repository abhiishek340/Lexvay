package com.lexvay.observability;

import com.lexvay.assessment.Finding;
import com.lexvay.assessment.MappingMethod;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobType;
import com.lexvay.llm.LlmPurpose;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Every Lexvay meter lives here. Call sites report facts (a model call finished, a quote was rejected);
 * only this class knows meter names and tags. Tags are small closed sets (enums and fixed words), never
 * identifiers, and no value is ever document text, a quote, rule text, a prompt or model output.
 */
@Component
public class LexvayMetrics {

    public enum LlmOutcome { SUCCESS, REFUSED, ERROR }

    public enum JobOutcome { DONE, RETRIED, FAILED, DEFERRED }

    private final MeterRegistry registry;

    public LexvayMetrics(MeterRegistry registry, JobRepository jobs) {
        this.registry = registry;
        Gauge.builder("lexvay.job.queue.depth", jobs, JobRepository::queuedCount)
                .description("Jobs waiting to be claimed, across all tenants")
                .register(registry);
    }

    /** One model call, successful or not. Token counts are absent for calls that produced no response. */
    public void llmCall(LlmPurpose purpose, String model, LlmOutcome outcome, Duration duration,
                        Integer inputTokens, Integer outputTokens) {
        Counter.builder("lexvay.llm.calls").description("Model calls")
                .tag("purpose", purpose.name()).tag("model", model).tag("outcome", word(outcome))
                .register(registry).increment();
        Timer.builder("lexvay.llm.duration").description("Wall time of model calls")
                .tag("purpose", purpose.name())
                .register(registry).record(duration);
        tokens("input", inputTokens);
        tokens("output", outputTokens);
    }

    /** A job handler finished: done, requeued for another attempt, or failed for good. */
    public void jobFinished(JobType type, JobOutcome outcome, Duration duration) {
        Timer.builder("lexvay.job.duration").description("Wall time of one job attempt")
                .tag("type", type.name()).tag("outcome", word(outcome))
                .register(registry).record(duration);
    }

    /** A quote proposed by the model was checked in code against the stored page text. */
    public void quoteVerification(boolean verified) {
        Counter.builder("lexvay.quote.verification").description("Model quotes checked against stored page text")
                .tag("result", verified ? "verified" : "rejected")
                .register(registry).increment();
    }

    /** A proposed mapping was stored. */
    public void mappingFinding(Finding finding, MappingMethod method) {
        Counter.builder("lexvay.mapping.finding").description("Mappings stored, by finding and how it was produced")
                .tag("finding", finding.name()).tag("method", method.name())
                .register(registry).increment();
    }

    private void tokens(String direction, Integer count) {
        if (count != null && count > 0) {
            Counter.builder("lexvay.llm.tokens").description("Model tokens")
                    .tag("direction", direction).register(registry).increment(count);
        }
    }

    private static String word(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
