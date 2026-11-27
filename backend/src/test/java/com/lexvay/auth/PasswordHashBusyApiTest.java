package com.lexvay.auth;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * D-407 inside the real filter chain: two permits, shared by sign-in and by choosing a password. The permits are held by
 * the test itself, so the refusal is deterministic and not a race.
 */
@TestPropertySource(properties = "lexvay.security.password-hash-concurrency=2")
class PasswordHashBusyApiTest extends AbstractDatabaseTest {

    /** The configured gate that also records how many hashes were inside it at once. */
    static final class CountingGate extends PasswordHashGate {
        final AtomicInteger inside = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final AtomicInteger entered = new AtomicInteger();

        CountingGate(PasswordHashProperties properties) {
            super(properties);
        }

        @Override
        public <T> T run(Supplier<T> hash) {
            return super.run(() -> {
                entered.incrementAndGet();
                peak.accumulateAndGet(inside.incrementAndGet(), Math::max);
                try {
                    return hash.get();
                } finally {
                    inside.decrementAndGet();
                }
            });
        }

        void reset() {
            inside.set(0);
            peak.set(0);
            entered.set(0);
        }
    }

    @TestConfiguration
    static class CountingGateConfig {
        @Bean
        @Primary
        CountingGate countingGate(PasswordHashProperties properties) {
            return new CountingGate(properties);
        }
    }

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired CountingGate gate;
    @Autowired PasswordHashProperties properties;

    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        t = tenants.create("busy");
        gate.reset();
    }

    private MvcTestResult login(String password) {
        return mvc.post().uri("/api/v1/auth/login").with(csrf())
                .param("username", t.reviewer().email()).param("password", password).exchange();
    }

    private MvcTestResult setup(String code, String password) {
        return mvc.post().uri("/api/v1/auth/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", code, "password", password))).exchange();
    }

    /** Runs {@code action} while both permits are held by this thread, so any hash it asks for has to wait. */
    private <T> T whileEveryPermitIsHeld(Supplier<T> action) {
        return gate.run(() -> gate.run(action));
    }

    private void assertBusy(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(503);
        assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("2");
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
        assertThat(json.readTree(result.getResponse().getContentAsString()).get("detail").asString())
                .isEqualTo("Sign-in is busy. Try again in a moment.");
    }

    @Test
    void theConfiguredNumberOfPermitsIsUsed() {
        assertThat(properties.permits()).isEqualTo(2);
    }

    @Test
    void aSingleSignInSucceeds() {
        assertThat(login(TestTenants.PASSWORD)).hasStatus(200);
        assertThat(gate.entered).as("one comparison, through the gate").hasValue(1);
    }

    @Test
    void aSignInThatCannotGetAPermitIsAnswered503WithRetryAfterAndComparesNothing() throws Exception {
        gate.reset();
        MvcTestResult busy = whileEveryPermitIsHeld(() -> login(TestTenants.PASSWORD));
        assertBusy(busy);
        assertThat(gate.entered).as("only the two holders entered").hasValue(2);

        assertThat(login(TestTenants.PASSWORD)).as("the permits came back").hasStatus(200);
    }

    @Test
    void anUnknownEmailIsAlsoRefusedWhenBusyBecauseItsDummyHashUsesAPermit() throws Exception {
        MvcTestResult busy = whileEveryPermitIsHeld(() -> mvc.post().uri("/api/v1/auth/login").with(csrf())
                .param("username", "nobody@nowhere.test").param("password", "whatever-it-is").exchange());

        assertBusy(busy);
    }

    @Test
    void choosingAPasswordSharesTheGateAndDoesNotUseUpTheCode() throws Exception {
        String email = "busy.person." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        var created = mvc.post().uri("/api/v1/users").with(user(t.admin())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "displayName", "Busy Person", "role", "REVIEWER"))).exchange();
        assertThat(created).hasStatus(201);
        JsonNode body = json.readTree(created.getResponse().getContentAsString());
        String code = body.get("setupCode").asString();

        assertBusy(whileEveryPermitIsHeld(() -> setup(code, "a-good-long-password")));

        assertThat(setup(code, "a-good-long-password")).as("the same code still works").hasStatus(204);
    }

    @Test
    void sixConcurrentSignInsCompleteOrAre503AndNeverHashMoreThanTwoAtOnce() throws Exception {
        gate.reset();
        var pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                statuses.add(pool.submit(() -> login(TestTenants.PASSWORD).getResponse().getStatus()));
            }
            for (Future<Integer> status : statuses) {
                assertThat(status.get(60, TimeUnit.SECONDS)).isIn(200, 503);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(gate.peak.get()).isBetween(1, 2);
        assertThat(gate.inside).as("every permit was returned").hasValue(0);
    }
}
