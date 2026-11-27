package com.lexvay.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** D-407: at most N password hashes at once, in arrival order, and nothing is hashed for a caller that gives up waiting. */
class PasswordHashGateTest {

    /** An encoder that counts how many calls are inside it, and holds each for a while. */
    private static class CountingEncoder implements PasswordEncoder {
        final AtomicInteger inside = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final long holdMillis;
        volatile CountDownLatch release;

        CountingEncoder(long holdMillis) {
            this.holdMillis = holdMillis;
        }

        private void hold() {
            calls.incrementAndGet();
            peak.accumulateAndGet(inside.incrementAndGet(), Math::max);
            entered.countDown();
            try {
                if (release != null) {
                    release.await();
                } else {
                    Thread.sleep(holdMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inside.decrementAndGet();
            }
        }

        @Override
        public String encode(CharSequence rawPassword) {
            hold();
            return "hash";
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            hold();
            return true;
        }
    }

    @Test
    void sixConcurrentCallersWithTwoPermitsAllFinishAndNeverHashMoreThanTwoAtOnce() throws Exception {
        var counting = new CountingEncoder(50);
        var encoder = new GatedPasswordEncoder(counting, new PasswordHashGate(2, Duration.ofSeconds(30)));
        var pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<?>> done = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                int n = i;
                done.add(pool.submit(() -> {
                    if (n % 2 == 0) {
                        encoder.encode("password " + n);
                    } else {
                        encoder.matches("password " + n, "hash");
                    }
                }));
            }
            for (Future<?> f : done) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(counting.calls).as("encode and matches share the permits").hasValue(6);
        assertThat(counting.peak.get()).isEqualTo(2);
    }

    @Test
    void aCallerThatWaitsTooLongIsRefusedAndNothingIsHashedForIt() throws Exception {
        var counting = new CountingEncoder(0);
        counting.release = new CountDownLatch(1);
        var encoder = new GatedPasswordEncoder(counting, new PasswordHashGate(1, Duration.ofMillis(100)));
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> holder = pool.submit(() -> encoder.matches("held", "hash"));
            assertThat(counting.entered.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> encoder.matches("refused", "hash")).isInstanceOf(PasswordHashBusyException.class)
                    .hasMessage("Sign-in is busy. Try again in a moment.");
            assertThatThrownBy(() -> encoder.encode("refused")).isInstanceOf(PasswordHashBusyException.class);
            assertThat(counting.calls).as("only the holder was hashed or compared").hasValue(1);

            counting.release.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(encoder.matches("after", "hash")).as("the permit came back").isTrue();
        } finally {
            counting.release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void aPermitIsReturnedWhenTheHashFails() {
        var gate = new PasswordHashGate(1, Duration.ofMillis(100));

        assertThatThrownBy(() -> gate.run(() -> {
            throw new IllegalStateException("broken");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(gate.run(() -> "next")).isEqualTo("next");
    }

    @Test
    void upgradeEncodingHashesNothingAndNeedsNoPermit() {
        var counting = new CountingEncoder(0) {
            @Override
            public boolean upgradeEncoding(String encodedPassword) {
                return true;
            }
        };
        var gate = new PasswordHashGate(1, Duration.ofMillis(10));
        var encoder = new GatedPasswordEncoder(counting, gate);

        gate.run(() -> {
            assertThat(encoder.upgradeEncoding("{bcrypt}x")).isTrue();
            return null;
        });
    }

    @Test
    void atLeastOnePermitIsRequired() {
        assertThatThrownBy(() -> new PasswordHashGate(0, Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Configuration
    @EnableConfigurationProperties(PasswordHashProperties.class)
    static class PropertiesOnly {
    }

    @Test
    void theConcurrencyDefaultsToTheProcessorsWithAFloorOfTwoAndMustBePositive() {
        var runner = new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

        runner.run(context -> assertThat(context.getBean(PasswordHashProperties.class).permits())
                .isEqualTo(Math.max(2, Runtime.getRuntime().availableProcessors())));
        runner.withPropertyValues("lexvay.security.password-hash-concurrency=")
                .run(context -> assertThat(context.getBean(PasswordHashProperties.class).permits())
                        .as("the empty value of the environment variable means unset").isGreaterThanOrEqualTo(2));
        runner.withPropertyValues("lexvay.security.password-hash-concurrency=3")
                .run(context -> assertThat(context.getBean(PasswordHashProperties.class).permits()).isEqualTo(3));
        for (String invalid : List.of("0", "-1")) {
            runner.withPropertyValues("lexvay.security.password-hash-concurrency=" + invalid)
                    .run(context -> assertThat(context).as("value %s", invalid).hasFailed());
        }
    }
}
