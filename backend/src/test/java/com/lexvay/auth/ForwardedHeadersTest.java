package com.lexvay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Behind a load balancer ({@code LEXVAY_FORWARD_HEADERS=native}) the client address and the scheme come from
 * the proxy's headers, so each real client has its own login budget and HSTS is sent for HTTPS clients (D-093).
 * The test client connects from the loopback address, which the container trusts as a proxy.
 */
@TestPropertySource(properties = {"LEXVAY_FORWARD_HEADERS=native", "lexvay.ratelimit.login-per-minute=2"})
class ForwardedHeadersTest extends AbstractDatabaseTest {

    @LocalServerPort int port;

    private HttpResponse<Void> send(String path, String... headers) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
            for (int i = 0; i < headers.length; i += 2) {
                request.header(headers[i], headers[i + 1]);
            }
            return http.send(request.build(), HttpResponse.BodyHandlers.discarding());
        }
    }

    /** A new connection per call, as separate clients have. */
    private int loginAttempt(String forwardedFor) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + SecurityConfig.LOGIN_URL))
                    .header("X-Forwarded-For", forwardedFor)
                    .header("Cookie", "XSRF-TOKEN=abc").header("X-XSRF-TOKEN", "abc")
                    .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString("username=nobody%40x.test&password=x")).build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    @Test
    void loginBudgetsAreKeyedByTheForwardedClientAddress() throws Exception {
        assertThat(loginAttempt("203.0.113.1")).isEqualTo(401);
        assertThat(loginAttempt("203.0.113.1")).isEqualTo(401);
        assertThat(loginAttempt("203.0.113.1")).as("third attempt from the same client").isEqualTo(429);
        assertThat(loginAttempt("203.0.113.2")).as("another client behind the same proxy").isEqualTo(401);
    }

    @Test
    void hstsFollowsTheForwardedScheme() throws Exception {
        var secure = send("/actuator/health", "X-Forwarded-Proto", "https");
        assertThat(secure.headers().firstValue("Strict-Transport-Security")).hasValue("max-age=31536000 ; includeSubDomains");
        var plain = send("/actuator/health");
        assertThat(plain.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }
}
