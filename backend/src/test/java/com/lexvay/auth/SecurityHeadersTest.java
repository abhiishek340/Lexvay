package com.lexvay.auth;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The response headers of D-093, checked on successes and on every kind of error the API produces. */
class SecurityHeadersTest extends AbstractDatabaseTest {

    static final String CSP = "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; "
            + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'";

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;

    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        t = tenants.create("headers");
    }

    private record Case(String name, HttpMethod method, String uri, boolean signedIn, boolean withCsrf, int status) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Case> responses() {
        return Stream.of(
                new Case("200 readiness probe, public", HttpMethod.GET, "/readyz", false, false, 200),
                new Case("200 JSON from the API", HttpMethod.GET, "/api/v1/programs", true, false, 200),
                new Case("401 not signed in", HttpMethod.GET, "/api/v1/programs", false, false, 401),
                new Case("403 missing CSRF token", HttpMethod.POST, "/api/v1/programs", true, false, 403),
                new Case("400 bad path id", HttpMethod.GET, "/api/v1/programs/not-a-uuid", true, false, 400),
                new Case("404 unknown API path", HttpMethod.GET, "/api/v1/nothing-here", true, false, 404),
                new Case("404 unknown record", HttpMethod.GET, "/api/v1/programs/7d1f0c2e-5b7a-4c1e-9d3a-0a1b2c3d4e5f", true, false, 404),
                new Case("405 wrong method", HttpMethod.DELETE, "/api/v1/programs", true, true, 405),
                new Case("415 wrong media type", HttpMethod.POST, "/api/v1/programs", true, true, 415),
                new Case("400 rejected by the request firewall", HttpMethod.GET, "/api/v1/programs;jsessionid=x", true, false, 400));
    }

    private MockHttpServletResponse send(Case c, RequestPostProcessor... extra) {
        var builder = mvc.method(c.method()).uri(c.uri());
        if (c.signedIn()) {
            builder.with(user(t.admin()));
        }
        if (c.withCsrf()) {
            builder.with(csrf());
        }
        if (c.status() == 415) {
            builder.contentType("text/plain").content("x");
        }
        for (var post : extra) {
            builder.with(post);
        }
        return builder.exchange().getResponse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responses")
    void everyResponseCarriesTheSecurityHeaders(Case c) {
        MockHttpServletResponse response = send(c);

        assertThat(response.getStatus()).isEqualTo(c.status());
        assertThat(response.getHeaders("Content-Security-Policy")).containsExactly(CSP);
        assertThat(response.getHeaders("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(response.getHeaders("Referrer-Policy")).containsExactly("no-referrer");
        assertThat(response.getHeaders("Permissions-Policy")).containsExactly("camera=(), microphone=(), geolocation=()");
        assertThat(response.getHeader("Strict-Transport-Security")).as("not sent over plain HTTP").isNull();
    }

    @ParameterizedTest(name = "{0} over HTTPS")
    @MethodSource("responses")
    void hstsIsSentForSecureRequests(Case c) {
        MockHttpServletResponse response = send(c, request -> {
            request.setSecure(true);
            request.setScheme("https");
            return request;
        });

        assertThat(response.getStatus()).isEqualTo(c.status());
        assertThat(response.getHeaders("Strict-Transport-Security")).containsExactly("max-age=31536000 ; includeSubDomains");
    }

    @Test
    void theDefaultFramingAndCachingProtectionsStayOn() {
        MockHttpServletResponse response = send(new Case("api", HttpMethod.GET, "/api/v1/programs", true, false, 200));
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }
}
