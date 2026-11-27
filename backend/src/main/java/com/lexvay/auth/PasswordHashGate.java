package com.lexvay.auth;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bounds how many password hashes run at once, because each Argon2id hash holds 32 MiB of heap (D-407). A fair semaphore:
 * callers are served in the order they arrived, and one that has waited {@value #WAIT_SECONDS} seconds is refused with
 * {@link PasswordHashBusyException} before anything is hashed or compared.
 */
@Component
public class PasswordHashGate {

    static final int WAIT_SECONDS = PasswordHashBusyException.RETRY_AFTER_SECONDS;

    private final Semaphore permits;
    private final Duration wait;

    @Autowired
    public PasswordHashGate(PasswordHashProperties properties) {
        this(properties.permits(), Duration.ofSeconds(WAIT_SECONDS));
    }

    PasswordHashGate(int permits, Duration wait) {
        if (permits < 1) {
            throw new IllegalArgumentException("At least one password hash must be allowed at a time");
        }
        this.permits = new Semaphore(permits, true);
        this.wait = wait;
    }

    public <T> T run(Supplier<T> hash) {
        try {
            if (!permits.tryAcquire(wait.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new PasswordHashBusyException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PasswordHashBusyException();
        }
        try {
            return hash.get();
        } finally {
            permits.release();
        }
    }
}
