package com.lexvay.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** What a provider charges for one model, in USD per million tokens. Both prices are positive. */
public record TokenPrice(BigDecimal inputPerMtok, BigDecimal outputPerMtok) {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    public TokenPrice {
        if (inputPerMtok.signum() <= 0 || outputPerMtok.signum() <= 0) {
            throw new IllegalArgumentException("Token prices must be positive");
        }
    }

    /** The charge for the tokens, rounded up to a millionth of a dollar: an estimate never errs low. */
    public BigDecimal cost(long inputTokens, long outputTokens) {
        return inputPerMtok.multiply(BigDecimal.valueOf(inputTokens))
                .add(outputPerMtok.multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 6, RoundingMode.UP);
    }
}
