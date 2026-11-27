package com.lexvay.assessment;

import com.lexvay.llm.LlmUsageRepository;
import com.lexvay.llm.LlmUsageRepository.PurposeUsage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What one assessment cost: model calls, tokens, time and an estimate in USD from the configured prices. */
@Service
public class AssessmentUsageService {

    private static final BigDecimal TOKENS_PER_PRICE_UNIT = BigDecimal.valueOf(1_000_000);

    public record Prices(BigDecimal inputPerMtok, BigDecimal outputPerMtok) {
    }

    /**
     * {@code wallTimeMs} is the assessment's own run time (null until it has finished); {@code modelTimeMs}
     * is the sum of the model calls' durations. The cost is an estimate: tokens times the configured prices.
     */
    public record UsageView(UUID assessmentId, String model, long calls, long inputTokens, long outputTokens,
                            long modelTimeMs, Long wallTimeMs, BigDecimal estimatedCostUsd, Prices prices,
                            List<PurposeUsage> byPurpose) {
    }

    private final AssessmentService assessments;
    private final LlmUsageRepository usage;
    private final Prices prices;

    public AssessmentUsageService(AssessmentService assessments, LlmUsageRepository usage,
                                  @Value("${lexvay.llm.price-input-per-mtok}") BigDecimal inputPerMtok,
                                  @Value("${lexvay.llm.price-output-per-mtok}") BigDecimal outputPerMtok) {
        this.assessments = assessments;
        this.usage = usage;
        this.prices = new Prices(inputPerMtok, outputPerMtok);
    }

    @Transactional(readOnly = true)
    public UsageView usage(UUID assessmentId) {
        Assessment assessment = assessments.require(assessmentId);
        List<PurposeUsage> byPurpose = usage.forAssessment(assessmentId);
        long calls = byPurpose.stream().mapToLong(PurposeUsage::calls).sum();
        long input = byPurpose.stream().mapToLong(PurposeUsage::inputTokens).sum();
        long output = byPurpose.stream().mapToLong(PurposeUsage::outputTokens).sum();
        long modelTime = byPurpose.stream().mapToLong(PurposeUsage::durationMs).sum();
        Long wallTime = assessment.startedAt() == null || assessment.finishedAt() == null ? null
                : Duration.between(assessment.startedAt(), assessment.finishedAt()).toMillis();
        return new UsageView(assessmentId, assessment.llmModel(), calls, input, output, modelTime, wallTime,
                cost(input, output), prices, byPurpose);
    }

    private BigDecimal cost(long inputTokens, long outputTokens) {
        return prices.inputPerMtok().multiply(BigDecimal.valueOf(inputTokens))
                .add(prices.outputPerMtok().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(TOKENS_PER_PRICE_UNIT, 6, RoundingMode.HALF_UP);
    }
}
