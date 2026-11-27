package com.lexvay.auth;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/** The limiter inside the real filter chain, with budgets small enough to exhaust (the other tests run with huge ones). */
@TestPropertySource(properties = {
        "lexvay.ratelimit.login-per-minute=3",
        "lexvay.ratelimit.api-per-minute=8",
        "lexvay.ratelimit.upload-per-minute=2"})
class RateLimitApiTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;

    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        t = tenants.create("ratelimit");
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private MvcTestResult login(String address, String password) {
        return mvc.post().uri(SecurityConfig.LOGIN_URL).with(csrf()).with(from(address))
                .param("username", t.signer().email()).param("password", password).exchange();
    }

    @Test
    void loginAttemptsFromOneAddressAreRefusedAfterTheBudget() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("192.0.2.10", "wrong")).hasStatus(401);
        }
        var refused = login("192.0.2.10", TestTenants.PASSWORD);
        assertThat(refused).hasStatus(429);
        assertThat(refused.getResponse().getContentType()).contains("application/problem+json");
        assertThat(refused.getResponse().getHeader("Content-Security-Policy")).as("security headers on a 429").isNotBlank();
        assertThat(Integer.parseInt(refused.getResponse().getHeader("Retry-After"))).isBetween(1, 20);
        var problem = json.readTree(refused.getResponse().getContentAsString());
        assertThat(problem.get("status").asInt()).isEqualTo(429);
        assertThat(problem.get("detail").asString()).contains("Too many requests");

        assertThat(login("192.0.2.11", TestTenants.PASSWORD)).as("another address").hasStatus(200);
    }

    @Test
    void attemptsWithoutAValidCsrfTokenCountToo() {
        for (int i = 0; i < 3; i++) {
            var withoutToken = mvc.post().uri(SecurityConfig.LOGIN_URL).with(from("192.0.2.20"))
                    .param("username", "a@b.test").param("password", "x").exchange();
            assertThat(withoutToken).hasStatus(403);
        }
        var refused = mvc.post().uri(SecurityConfig.LOGIN_URL).with(from("192.0.2.20"))
                .param("username", "a@b.test").param("password", "x").exchange();
        assertThat(refused).hasStatus(429);
    }

    @Test
    void authenticatedApiCallsAreLimitedPerUser() {
        for (int i = 0; i < 8; i++) {
            assertThat(mvc.get().uri("/api/v1/auth/me").with(user(t.reviewer())).exchange()).hasStatus(200);
        }
        var refused = mvc.get().uri("/api/v1/auth/me").with(user(t.reviewer())).exchange();
        assertThat(refused).hasStatus(429);
        assertThat(refused.getResponse().getContentType()).contains("application/problem+json");
        assertThat(refused.getResponse().getHeader("Retry-After")).isNotBlank();

        assertThat(mvc.get().uri("/api/v1/auth/me").with(user(t.signer())).exchange()).as("another user").hasStatus(200);
    }

    @Test
    void uploadsAreLimitedPerUser() throws Exception {
        var created = mvc.post().uri("/api/v1/programs").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "P", "licenseType", "L", "catalogYear", "2026-27", "jurisdictions", List.of("CA"))))
                .exchange();
        UUID programId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asString());

        for (int i = 0; i < 2; i++) {
            assertThat(upload(programId, "file " + i)).hasStatus(202);
        }
        var refused = upload(programId, "one too many");
        assertThat(refused).hasStatus(429);
        assertThat(refused.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(mvc.get().uri("/api/v1/programs").with(user(t.contributor())).exchange()).as("reads are unaffected").hasStatus(200);
    }

    private MvcTestResult upload(UUID programId, String content) {
        return mvc.perform(multipart("/api/v1/programs/" + programId + "/documents")
                .file(new MockMultipartFile("file", "a.txt", "text/plain", content.getBytes()))
                .param("kind", "HANDBOOK").with(user(t.contributor())).with(csrf()));
    }
}
