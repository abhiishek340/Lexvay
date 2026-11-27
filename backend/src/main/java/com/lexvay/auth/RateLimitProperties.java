package com.lexvay.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Request budgets per minute (D-092) and the bounds of the in-memory bucket cache. Buckets are per
 * instance: with several instances behind a load balancer the effective budget is multiplied.
 */
@Validated
@ConfigurationProperties("lexvay.ratelimit")
public record RateLimitProperties(
        @DefaultValue("10") @Positive int loginPerMinute,
        @DefaultValue("600") @Positive int apiPerMinute,
        @DefaultValue("30") @Positive int uploadPerMinute,
        @DefaultValue("10000") @Positive long maxKeys,
        @DefaultValue("10m") @NotNull Duration idleExpiry) {
}
