package com.lexvay.llm;

import com.lexvay.llm.LlmCallBlockedException.Reason;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * No metered call is made without explicit approval and a cost cap (D-562). A call to a billed provider is allowed
 * only when LEXVAY_LLM_PAID_APPROVED is true, the model has a configured price, and the cost recorded so far this UTC
 * month plus the most this call can cost stays within LEXVAY_LLM_MONTHLY_BUDGET_USD. The defaults, not approved and a
 * budget of zero, refuse everything, and so does an empty price table.
 *
 * <p>The most a call can cost is its largest possible output ({@code maxOutputTokens}) plus an input of one token per
 * byte of the system prompt, the user prompt and the output schema. A token is never shorter than a byte, so the input
 * figure is an upper bound. The recorded figure is what {@link LlmCallRecorder} stored, which is every successful call
 * of every tenant; a call that failed at the provider is not recorded, and calls in flight on other instances are not
 * yet, so several instances can overshoot the budget by what is in flight. Scripted calls are free and left out.
 */
@Component
public class SpendGate {

    private static final Logger log = LoggerFactory.getLogger(SpendGate.class);

    private final boolean approved;
    private final BigDecimal monthlyBudgetUsd;
    private final LlmRates rates;
    private final LlmUsageRepository usage;

    public SpendGate(@Value("${lexvay.llm.provider:fake}") String provider,
                     @Value("${lexvay.llm.paid-approved:false}") boolean approved,
                     @Value("${lexvay.llm.monthly-budget-usd:0}") BigDecimal monthlyBudgetUsd, LlmRates rates,
                     LlmUsageRepository usage) {
        if (monthlyBudgetUsd.signum() < 0) {
            throw new IllegalArgumentException("LEXVAY_LLM_MONTHLY_BUDGET_USD must not be negative");
        }
        this.approved = approved;
        this.monthlyBudgetUsd = monthlyBudgetUsd;
        this.rates = rates;
        this.usage = usage;
        log.info("LLM provider {}: paid calls approved {}, monthly budget {} USD, priced models {}", provider, approved,
                monthlyBudgetUsd.toPlainString(), rates.models());
    }

    /** Returns when the call may be made; otherwise says why not. Reads totals only, never what was sent or answered. */
    public void check(LlmRequest request, String model) {
        if (!approved) {
            throw blocked(Reason.PAID_CALLS_NOT_APPROVED, "Paid model calls are not approved (LEXVAY_LLM_PAID_APPROVED is not true)");
        }
        TokenPrice price = priceOf(model);
        BigDecimal worstCase = price.cost(inputTokenBound(request), request.maxOutputTokens());
        BigDecimal projected = recordedThisMonth().add(worstCase);
        if (projected.compareTo(monthlyBudgetUsd) > 0) {
            throw blocked(Reason.OVER_BUDGET, "The call could bring this month's cost to " + projected.toPlainString()
                    + " USD, over the monthly budget of " + monthlyBudgetUsd.toPlainString() + " USD");
        }
    }

    private BigDecimal recordedThisMonth() {
        BigDecimal total = BigDecimal.ZERO;
        for (LlmUsageRepository.ModelUsage used : usage.thisMonthExcluding(FakeLlmClient.MODEL_ID)) {
            if (used.inputTokens() > 0 || used.outputTokens() > 0) {
                total = total.add(priceOf(used.model()).cost(used.inputTokens(), used.outputTokens()));
            }
        }
        return total;
    }

    private TokenPrice priceOf(String model) {
        return rates.priceOf(model).orElseThrow(() -> blocked(Reason.COST_UNKNOWN,
                "No price is configured for model " + model + " (LEXVAY_LLM_RATES), so its cost is unknown"));
    }

    private static long inputTokenBound(LlmRequest request) {
        return bytes(request.systemPrompt()) + bytes(request.userPrompt()) + bytes(request.outputSchema());
    }

    private static long bytes(Object text) {
        return text == null ? 0 : Objects.toString(text).getBytes(StandardCharsets.UTF_8).length;
    }

    private static LlmCallBlockedException blocked(Reason reason, String message) {
        return new LlmCallBlockedException(reason, message);
    }
}
