package com.lexvay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/** The limiter without Spring: a fake clock makes refill and idle expiry exact. */
class RateLimitFilterTest {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = filter(new RateLimitProperties(3, 5, 2, 10_000, Duration.ofMinutes(10)));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private RateLimitFilter filter(RateLimitProperties limits) {
        return new RateLimitFilter(limits, new ProblemWriter(JsonMapper.builder().build()), nanos::get);
    }

    private MockHttpServletResponse send(String method, String uri, String remoteAddress) throws Exception {
        var request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddress);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse login(String remoteAddress) throws Exception {
        return send("POST", SecurityConfig.LOGIN_URL, remoteAddress);
    }

    private static void signInAs(UUID userId) {
        var user = new LexvayUser(userId, UUID.randomUUID(), "u@x.test", "U", Role.ADMIN, "hash", 0);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    @Test
    void loginAttemptsAreLimitedPerClientAddress() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("10.0.0.1").getStatus()).isEqualTo(200);
        }
        var refused = login("10.0.0.1");
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getContentType()).isEqualTo("application/problem+json");
        assertThat(refused.getContentAsString()).contains("\"status\":429").contains("Too many requests");
        assertThat(login("10.0.0.2").getStatus()).as("another address has its own budget").isEqualTo(200);
    }

    @Test
    void retryAfterIsTheWholeSecondsUntilATokenIsBack() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("10.0.0.1");
        }
        // Three tokens per minute refill one every 20 seconds.
        assertThat(login("10.0.0.1").getHeader("Retry-After")).isEqualTo("20");
        nanos.addAndGet(Duration.ofSeconds(8).toNanos());
        assertThat(login("10.0.0.1").getHeader("Retry-After")).isEqualTo("12");
    }

    @Test
    void theBudgetRefillsAsTimePasses() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("10.0.0.1");
        }
        assertThat(login("10.0.0.1").getStatus()).isEqualTo(429);
        nanos.addAndGet(Duration.ofSeconds(21).toNanos());
        assertThat(login("10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(login("10.0.0.1").getStatus()).isEqualTo(429);
        nanos.addAndGet(Duration.ofMinutes(1).toNanos());
        for (int i = 0; i < 3; i++) {
            assertThat(login("10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void theOidcCallbackCountsAsALoginAttempt() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(send("GET", "/login/oauth2/code/lexvay", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        assertThat(login("10.0.0.1").getStatus()).as("same budget as the form login").isEqualTo(429);
        assertThat(send("GET", "/login/oauth2/code/lexvay", "10.0.0.1").getStatus()).isEqualTo(429);
    }

    @Test
    void apiCallsAreLimitedPerUser() throws Exception {
        UUID alice = UUID.randomUUID();
        signInAs(alice);
        for (int i = 0; i < 5; i++) {
            assertThat(send("GET", "/api/v1/programs", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        assertThat(send("GET", "/api/v1/programs", "10.0.0.1").getStatus()).isEqualTo(429);
        assertThat(send("GET", "/api/v1/programs", "10.0.0.9").getStatus()).as("the key is the user, not the address").isEqualTo(429);

        signInAs(UUID.randomUUID());
        assertThat(send("GET", "/api/v1/programs", "10.0.0.1").getStatus()).as("another user").isEqualTo(200);
    }

    @Test
    void anonymousRequestsAndPathsOutsideTheApiAreNotCounted() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(send("GET", "/api/v1/auth/session", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        signInAs(UUID.randomUUID());
        for (int i = 0; i < 20; i++) {
            assertThat(send("GET", "/actuator/health", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void uploadsHaveTheirOwnTighterBudgetOnTopOfTheApiBudget() throws Exception {
        signInAs(UUID.randomUUID());
        String upload = "/api/v1/programs/" + UUID.randomUUID() + "/documents";
        assertThat(send("POST", upload, "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(send("POST", upload, "10.0.0.1").getStatus()).isEqualTo(200);
        var refused = send("POST", upload, "10.0.0.1");
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isNotNull();
        assertThat(send("GET", "/api/v1/programs", "10.0.0.1").getStatus()).as("reads still have budget").isEqualTo(200);
    }

    @Test
    void theApiBudgetCountsUploadsToo() throws Exception {
        signInAs(UUID.randomUUID());
        for (int i = 0; i < 4; i++) {
            send("GET", "/api/v1/programs", "10.0.0.1");
        }
        String upload = "/api/v1/programs/" + UUID.randomUUID() + "/documents";
        assertThat(send("POST", upload, "10.0.0.1").getStatus()).as("fifth api call").isEqualTo(200);
        assertThat(send("POST", upload, "10.0.0.1").getStatus()).as("sixth api call").isEqualTo(429);
    }

    @Test
    void theBucketCacheNeverHoldsMoreThanTheConfiguredNumberOfKeys() throws Exception {
        filter = filter(new RateLimitProperties(3, 5, 2, 100, Duration.ofMinutes(10)));
        for (int i = 0; i < 1_000; i++) {
            login("10.1." + i / 250 + "." + i % 250);
        }
        assertThat(filter.bucketCount()).isLessThanOrEqualTo(100);
    }

    @Test
    void idleKeysExpire() throws Exception {
        for (int i = 0; i < 50; i++) {
            login("10.2.0." + i);
        }
        assertThat(filter.bucketCount()).isEqualTo(50);
        nanos.addAndGet(Duration.ofMinutes(9).toNanos());
        login("10.2.0.0");
        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        assertThat(filter.bucketCount()).as("only the key touched after nine minutes survives").isEqualTo(1);
    }

    @Test
    void anExpiredBucketStartsFull() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("10.0.0.1");
        }
        nanos.addAndGet(Duration.ofMinutes(11).toNanos());
        assertThat(login("10.0.0.1").getStatus()).isEqualTo(200);
    }
}
