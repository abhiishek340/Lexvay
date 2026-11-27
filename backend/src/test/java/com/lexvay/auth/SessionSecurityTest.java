package com.lexvay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Session handling over a real HTTP connection, because MockMvc has no servlet container and therefore no
 * session cookie and no session id to change (D-094).
 */
class SessionSecurityTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @LocalServerPort int port;

    RestClient client;
    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        t = tenants.create("session");
        client = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private static String cookieValue(ResponseEntity<?> response, String name) {
        return response.getHeaders().getOrDefault("Set-Cookie", List.of()).stream()
                .filter(c -> c.startsWith(name + "=")).map(c -> c.substring(name.length() + 1).split(";")[0])
                .findFirst().orElse(null);
    }

    private static String setCookieHeader(ResponseEntity<?> response, String name) {
        return response.getHeaders().getOrDefault("Set-Cookie", List.of()).stream()
                .filter(c -> c.startsWith(name + "=")).findFirst().orElseThrow();
    }

    private ResponseEntity<String> login(String csrf, String sessionId) {
        var request = client.post().uri(SecurityConfig.LOGIN_URL)
                .header("Cookie", "XSRF-TOKEN=" + csrf + (sessionId == null ? "" : "; JSESSIONID=" + sessionId))
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=" + t.signer().email() + "&password=" + TestTenants.PASSWORD);
        return request.retrieve().toEntity(String.class);
    }

    private int meStatus(String sessionId) {
        return client.get().uri("/api/v1/auth/me").header("Cookie", "JSESSIONID=" + sessionId)
                .retrieve().toEntity(String.class).getStatusCode().value();
    }

    /** Anything that answers 401 to a browser-like request starts a session, which is what an attacker plants. */
    private String sessionBeforeLogin() {
        var anonymous = client.get().uri("/api/v1/programs").retrieve().toEntity(String.class);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        return cookieValue(anonymous, "JSESSIONID");
    }

    @Test
    void loginChangesTheSessionId() {
        String planted = sessionBeforeLogin();
        assertThat(planted).as("a session exists before login").isNotBlank();

        var response = login("csrf-token-value", planted);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String issued = cookieValue(response, "JSESSIONID");

        assertThat(issued).as("session id after login").isNotBlank().isNotEqualTo(planted);
        assertThat(meStatus(issued)).as("new session is authenticated").isEqualTo(200);
        assertThat(meStatus(planted)).as("planted session is useless").isEqualTo(401);
    }

    @Test
    void loginWithoutAnyCsrfHeaderIsRefused() {
        var response = client.post().uri(SecurityConfig.LOGIN_URL)
                .header("Cookie", "XSRF-TOKEN=abc")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=" + t.signer().email() + "&password=" + TestTenants.PASSWORD)
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void sessionCookieIsHttpOnlyAndSameSiteLaxButNotSecureOverPlainHttp() {
        var response = login("csrf-token-value", null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String cookie = setCookieHeader(response, "JSESSIONID");
        assertThat(cookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/").doesNotContain("Secure");
    }
}
