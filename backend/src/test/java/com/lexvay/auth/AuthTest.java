package com.lexvay.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static com.lexvay.common.TestCsrf.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.audit.AuditRepository;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AuthTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired AuditRepository audit;
    @Autowired org.springframework.context.ApplicationContext context;
    @LocalServerPort int port;

    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        t = tenants.create("auth");
    }

    @Test
    void loginWithCorrectPasswordReturnsUserAndIsAudited() throws Exception {
        var result = mvc.post().uri("/api/v1/auth/login").with(csrf())
                .param("username", t.signer().email()).param("password", TestTenants.PASSWORD)
                .exchange();
        assertThat(result).hasStatus(200);
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("email").asString()).isEqualTo(t.signer().email());
        assertThat(body.get("role").asString()).isEqualTo("SIGNER");
        assertThat(body.get("tenantId").asString()).isEqualTo(t.tenantId().toString());
        assertThat(body.has("passwordHash")).isFalse();

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(t.signer().id()));
        assertThat(entries).extracting(e -> e.action()).contains("LOGIN_SUCCEEDED");
    }

    @Test
    void loginWithWrongPasswordIs401ProblemDetail() {
        var result = mvc.post().uri("/api/v1/auth/login").with(csrf())
                .param("username", t.signer().email()).param("password", "wrong")
                .exchange();
        assertThat(result).hasStatus(401);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
    }

    @Test
    void loginWithUnknownEmailIs401() {
        var result = mvc.post().uri("/api/v1/auth/login").with(csrf())
                .param("username", "nobody@nowhere.test").param("password", "x")
                .exchange();
        assertThat(result).hasStatus(401);
    }

    @Test
    void singleSignOnIsOffUnlessProfileOidcIsActive() {
        assertThat(context.getBeanNamesForType(OidcLogin.class)).isEmpty();
        assertThat(context.getBeanNamesForType(org.springframework.security.oauth2.client.registration.ClientRegistrationRepository.class)).isEmpty();
        assertThat(mvc.get().uri("/oauth2/authorization/lexvay").exchange()).hasStatus(401);
    }

    @Test
    void loginIsAuditedWithItsMethod() {
        mvc.post().uri("/api/v1/auth/login").with(csrf())
                .param("username", t.admin().email()).param("password", TestTenants.PASSWORD).exchange();
        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(t.admin().id()));
        var login = entries.stream().filter(e -> e.action().equals("LOGIN_SUCCEEDED")).findFirst().orElseThrow();
        assertThat(login.detail()).containsEntry("method", "password");
    }

    @Test
    void meReturnsTheAuthenticatedUser() throws Exception {
        var result = mvc.get().uri("/api/v1/auth/me").with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        assertThat(json.readTree(result.getResponse().getContentAsString()).get("displayName").asString())
                .isEqualTo(t.reviewer().displayName());
    }

    @Test
    void unauthenticatedRequestIs401ProblemDetail() {
        var result = mvc.get().uri("/api/v1/auth/me").exchange();
        assertThat(result).hasStatus(401);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
    }

    /**
     * The single-page app's real flow over HTTP: GET /session sets the CSRF cookie and names the header,
     * then login succeeds only when that header carries the cookie value. (MockMvc's csrf() helper swaps
     * the CSRF plumbing inside the shared context, so this test goes through the real server port.)
     */
    @Test
    void sessionEndpointSetsCsrfCookieThatLoginAccepts() throws Exception {
        var client = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();

        var session = client.get().uri("/api/v1/auth/session").retrieve().toEntity(String.class);
        assertThat(session.getStatusCode().value()).isEqualTo(200);
        var body = json.readTree(session.getBody());
        assertThat(body.get("authenticated").asBoolean()).isFalse();
        String headerName = body.get("csrfHeaderName").asString();
        assertThat(headerName).isNotBlank();

        String setCookie = String.join(";", session.getHeaders().getOrDefault("Set-Cookie", List.of()));
        assertThat(setCookie).as("SPA CSRF cookie").contains("XSRF-TOKEN=");
        assertThat(setCookie.toLowerCase()).as("readable by the SPA").doesNotContain("httponly");
        String token = setCookie.replaceAll(".*XSRF-TOKEN=([^;]+).*", "$1");

        var withoutHeader = client.post().uri("/api/v1/auth/login")
                .header("Cookie", "XSRF-TOKEN=" + token)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=" + t.signer().email() + "&password=" + TestTenants.PASSWORD)
                .retrieve().toEntity(String.class);
        assertThat(withoutHeader.getStatusCode().value()).as("login without CSRF header").isEqualTo(403);

        var withHeader = client.post().uri("/api/v1/auth/login")
                .header("Cookie", "XSRF-TOKEN=" + token)
                .header(headerName, token)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=" + t.signer().email() + "&password=" + TestTenants.PASSWORD)
                .retrieve().toEntity(String.class);
        assertThat(withHeader.getStatusCode().value()).as("login with CSRF header").isEqualTo(200);
        assertThat(json.readTree(withHeader.getBody()).get("role").asString()).isEqualTo("SIGNER");
    }

    @Test
    void sessionEndpointReportsTheSignedInUser() throws Exception {
        var result = mvc.get().uri("/api/v1/auth/session").with(user(t.signer())).exchange();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("authenticated").asBoolean()).isTrue();
        assertThat(body.get("user").get("role").asString()).isEqualTo("SIGNER");
    }

    @Test
    void logoutIs204AndAudited() {
        var result = mvc.post().uri("/api/v1/auth/logout").with(user(t.contributor())).with(csrf()).exchange();
        assertThat(result).hasStatus(204);
        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(t.contributor().id()));
        assertThat(entries).extracting(e -> e.action()).contains("LOGOUT");
    }
}
