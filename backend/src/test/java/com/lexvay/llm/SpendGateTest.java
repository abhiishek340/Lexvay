package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexvay.llm.LlmCallBlockedException.Reason;
import com.lexvay.llm.LlmUsageRepository.ModelUsage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The spend gate's decisions, with the month's recorded usage given by a stub. */
class SpendGateTest {

    private static final String MODEL = "priced-model";
    /** 14 bytes of input and 1000 output tokens at 3.00 and 15.00 per million: 0.015042 USD at most. */
    private static final LlmRequest REQUEST = new LlmRequest(LlmPurpose.MATCH_EVIDENCE, "MATCH:X-1", "ssss", "uuuuuuuuuu", null, 1000);
    private static final String RATES = MODEL + "=3.00:15.00";

    private final LlmUsageRepository usage = mock(LlmUsageRepository.class);

    private SpendGate gate(boolean approved, String budget, String rates) {
        return new SpendGate("anthropic", approved, new BigDecimal(budget), LlmRates.parse(rates), usage);
    }

    private void recorded(ModelUsage... used) {
        when(usage.thisMonthExcluding(FakeLlmClient.MODEL_ID)).thenReturn(List.of(used));
    }

    private static void assertRefused(SpendGate gate, Reason reason) {
        assertThatThrownBy(() -> gate.check(REQUEST, MODEL)).isInstanceOfSatisfying(LlmCallBlockedException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void theDefaultsRefuseEverything() {
        assertRefused(gate(false, "0", ""), Reason.PAID_CALLS_NOT_APPROVED);
    }

    @Test
    void withoutApprovalNothingIsReadAndNothingElseMatters() {
        assertRefused(gate(false, "1000", RATES), Reason.PAID_CALLS_NOT_APPROVED);

        verifyNoInteractions(usage);
    }

    @Test
    void approvalAloneIsNotEnoughBecauseTheCostIsUnknownWithoutAPrice() {
        assertRefused(gate(true, "1000", ""), Reason.COST_UNKNOWN);
        assertRefused(gate(true, "1000", "another-model=3.00:15.00"), Reason.COST_UNKNOWN);

        verifyNoInteractions(usage);
    }

    @Test
    void approvalAndAPriceAreNotEnoughWithTheBudgetAtZero() {
        recorded();

        assertRefused(gate(true, "0", RATES), Reason.OVER_BUDGET);
    }

    @Test
    void aCallThatFitsTheBudgetIsAllowed() {
        recorded();

        assertThatCode(() -> gate(true, "1.00", RATES).check(REQUEST, MODEL)).doesNotThrowAnyException();
        verify(usage).thisMonthExcluding(FakeLlmClient.MODEL_ID);
    }

    @Test
    void theRecordedCostAndTheCallsLargestCostAreAddedAndOnlyAnOvershootRefuses() {
        // 100,000 input tokens at 3.00 and 40,000 output tokens at 15.00 per million: 0.90 USD, plus 0.015042 for the call.
        recorded(new ModelUsage(MODEL, 100_000, 40_000));

        assertThatCode(() -> gate(true, "0.915042", RATES).check(REQUEST, MODEL)).as("exactly the budget").doesNotThrowAnyException();
        assertRefused(gate(true, "0.915041", RATES), Reason.OVER_BUDGET);
    }

    @Test
    void theRefusalNamesTheFiguresAndNothingFromTheRequest() {
        recorded(new ModelUsage(MODEL, 100_000, 40_000));

        assertThatThrownBy(() -> gate(true, "0.50", RATES).check(REQUEST, MODEL))
                .hasMessageContaining("0.915042").hasMessageContaining("0.50")
                .hasMessageNotContaining("ssss").hasMessageNotContaining("uuuuuuuuuu");
    }

    @Test
    void aModelWithRecordedUseAndNoPriceMakesTheMonthsCostUnknownAndRefuses() {
        recorded(new ModelUsage("older-model", 10, 10));

        assertRefused(gate(true, "1000", RATES), Reason.COST_UNKNOWN);
    }

    @Test
    void aModelWithNoRecordedTokensCostsNothingWhateverItsPrice() {
        recorded(new ModelUsage("calls-without-token-counts", 0, 0));

        assertThatCode(() -> gate(true, "1.00", RATES).check(REQUEST, MODEL)).doesNotThrowAnyException();
    }

    @Test
    void theInputBoundCountsBytesNotCharacters() {
        // "é" is two bytes, and the empty schema "{}" two more: six input tokens at 1.00 and nothing out.
        LlmRequest accents = new LlmRequest(LlmPurpose.MATCH_EVIDENCE, "k", "é", "é", JsonMapper.builder().build().createObjectNode(), 0);
        recorded();

        assertThatThrownBy(() -> gate(true, "0.000005", MODEL + "=1.00:1.00").check(accents, MODEL))
                .isInstanceOfSatisfying(LlmCallBlockedException.class, e -> assertThat(e.reason()).isEqualTo(Reason.OVER_BUDGET));
        assertThatCode(() -> gate(true, "0.000006", MODEL + "=1.00:1.00").check(accents, MODEL)).doesNotThrowAnyException();
    }

    @Test
    void aNegativeBudgetIsAConfigurationError() {
        assertThatThrownBy(() -> gate(true, "-1", RATES)).isInstanceOf(IllegalArgumentException.class);
    }
}
