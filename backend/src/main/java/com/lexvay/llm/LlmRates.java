package com.lexvay.llm;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The configured price table: one {@link TokenPrice} per model id. Nothing is built in, because a price in the code
 * goes stale without anyone noticing; an empty table means every cost is unknown and the spend gate refuses the call
 * (D-562). Written as {@code model=inputPerMtok:outputPerMtok}, entries separated by commas, for example
 * {@code some-model=1.00:5.00,other-model=3.00:15.00}.
 */
public final class LlmRates {

    private final Map<String, TokenPrice> byModel;

    private LlmRates(Map<String, TokenPrice> byModel) {
        this.byModel = byModel;
    }

    public static LlmRates parse(String spec) {
        Map<String, TokenPrice> table = new LinkedHashMap<>();
        if (spec != null && !spec.isBlank()) {
            for (String entry : spec.split(",")) {
                String[] model = entry.trim().split("=", -1);
                String[] prices = model.length == 2 ? model[1].split(":", -1) : new String[0];
                if (model[0].isBlank() || prices.length != 2) {
                    throw new IllegalArgumentException("LEXVAY_LLM_RATES entries are model=inputPerMtok:outputPerMtok, not '" + entry.trim() + "'");
                }
                if (table.put(model[0], price(entry, prices)) != null) {
                    throw new IllegalArgumentException("LEXVAY_LLM_RATES lists " + model[0] + " twice");
                }
            }
        }
        return new LlmRates(Map.copyOf(table));
    }

    private static TokenPrice price(String entry, String[] prices) {
        try {
            return new TokenPrice(new BigDecimal(prices[0].trim()), new BigDecimal(prices[1].trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("LEXVAY_LLM_RATES needs two positive numbers in '" + entry.trim() + "'", e);
        }
    }

    public Optional<TokenPrice> priceOf(String model) {
        return Optional.ofNullable(byModel.get(model));
    }

    public List<String> models() {
        return byModel.keySet().stream().sorted().toList();
    }
}
