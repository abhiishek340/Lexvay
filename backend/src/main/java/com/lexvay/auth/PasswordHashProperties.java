package com.lexvay.auth;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code lexvay.security.password-hash-concurrency}: how many password hashes may run at once (D-407). Each holds 32 MiB
 * of heap while it runs (D-400). Unset means the number of processors, and at least 2.
 */
@Validated
@ConfigurationProperties("lexvay.security")
public record PasswordHashProperties(@Positive Integer passwordHashConcurrency) {

    public int permits() {
        return passwordHashConcurrency != null ? passwordHashConcurrency : Math.max(2, Runtime.getRuntime().availableProcessors());
    }
}
