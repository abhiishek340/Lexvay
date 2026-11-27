package com.lexvay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

/** {@code LEXVAY_COOKIE_SECURE=true} (production behind HTTPS) marks the session cookie Secure (D-093). */
@TestPropertySource(properties = "LEXVAY_COOKIE_SECURE=true")
class SecureCookieTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @LocalServerPort int port;

    @Test
    void sessionCookieIsSecureHttpOnlyAndSameSiteLax() {
        var t = tenants.create("securecookie");
        var client = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();

        var response = client.post().uri(SecurityConfig.LOGIN_URL)
                .header("Cookie", "XSRF-TOKEN=abc").header("X-XSRF-TOKEN", "abc")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=" + t.signer().email() + "&password=" + TestTenants.PASSWORD)
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String cookie = response.getHeaders().getOrDefault("Set-Cookie", List.of()).stream()
                .filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow();
        assertThat(cookie).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
    }
}
