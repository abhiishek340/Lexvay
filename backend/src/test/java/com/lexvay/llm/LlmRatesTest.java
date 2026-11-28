package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LlmRatesTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void noConfigurationIsAnEmptyTableSoEveryCostIsUnknown(String spec) {
        LlmRates rates = LlmRates.parse(spec);

        assertThat(rates.models()).isEmpty();
        assertThat(rates.priceOf("any-model")).isEmpty();
    }

    @Test
    void entriesGivePricesPerModel() {
        LlmRates rates = LlmRates.parse("model-a=1.50:7.5, model-b=3:15");

        assertThat(rates.models()).containsExactly("model-a", "model-b");
        assertThat(rates.priceOf("model-a")).contains(new TokenPrice(new BigDecimal("1.50"), new BigDecimal("7.5")));
        assertThat(rates.priceOf("model-b").orElseThrow().inputPerMtok()).isEqualByComparingTo("3");
        assertThat(rates.priceOf("model-c")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"model-a", "model-a=1.00", "model-a=1.00:", "=1.00:2.00", "model-a=x:2.00", "model-a=0:2.00",
            "model-a=1.00:-2.00", "model-a=1:2,model-a=3:4", "model-a=1:2:3"})
    void anEntryThatIsNotAModelAndTwoPositivePricesIsRejectedAtStartup(String spec) {
        assertThatThrownBy(() -> LlmRates.parse(spec)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("LEXVAY_LLM_RATES");
    }

    @Test
    void aCostIsRoundedUpToAMillionthOfADollar() {
        TokenPrice price = new TokenPrice(new BigDecimal("3.00"), new BigDecimal("15.00"));

        assertThat(price.cost(1, 0)).isEqualByComparingTo("0.000003");
        assertThat(price.cost(0, 1)).isEqualByComparingTo("0.000015");
        assertThat(new TokenPrice(new BigDecimal("0.1"), new BigDecimal("0.1")).cost(1, 0)).isEqualByComparingTo("0.000001");
        assertThat(price.cost(1_000_000, 1_000_000)).isEqualByComparingTo("18");
    }
}
