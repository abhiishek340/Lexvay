package com.lexvay.assessment;

import com.lexvay.llm.LlmRates;
import com.lexvay.llm.LlmUsageRepository;
import com.lexvay.llm.LlmUsageRepository.PurposeUsage;
import com.lexvay.llm.TokenPrice;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What one assessment cost: model calls, tokens, time and an estimate in USD from the configured prices. */
@Service
public class AssessmentUsageService {

    /**
     * {@code wallTimeMs} is the assessment's own run time (null until it has finished); {@code modelTimeMs}
     * is the sum of the model calls' durations. The cost is an estimate, tokens times the price configured for the
     * assessment's model in LEXVAY_LLM_RATES, and {@code prices} and the cost are null when that model has none.
     */
    public record UsageView(UUID assessmentId, String model, long calls, long inputTokens, long outputTokens,
                            long modelTimeMs, Long wallTimeMs, BigDecimal estimatedCostUsd, TokenPrice prices,
                            List<PurposeUsage> byPurpose) {
    }

    private final AssessmentService assessments;
    private final LlmUsageRepository usage;
    private final LlmRates rates;

    public AssessmentUsageService(AssessmentService assessments, LlmUsageRepository usage, LlmRates rates) {
        this.assessments = assessments;
        this.usage = usage;
        this.rates = rates;
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
        Optional<TokenPrice> price = rates.priceOf(assessment.llmModel());
        return new UsageView(assessmentId, assessment.llmModel(), calls, input, output, modelTime, wallTime,
                price.map(p -> p.cost(input, output)).orElse(null), price.orElse(null), byPurpose);
    }
}
