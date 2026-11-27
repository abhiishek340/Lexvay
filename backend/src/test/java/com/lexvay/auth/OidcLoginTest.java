package com.lexvay.auth;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.lexvay.audit.AuditRepository;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Single sign-on through profile {@code oidc} (D-090, D-091), driven through the whole authorization-code
 * flow against {@link FakeIdentityProvider}: authorization redirect, state and nonce, token exchange, ID token
 * signature check, then the Lexvay account lookup.
 */
@ActiveProfiles("oidc")
class OidcLoginTest extends AbstractDatabaseTest {

    private static final FakeIdentityProvider IDP = new FakeIdentityProvider();

    @DynamicPropertySource
    static void identityProvider(DynamicPropertyRegistry registry) {
        registry.add("LEXVAY_OIDC_CLIENT_ID", () -> FakeIdentityProvider.CLIENT_ID);
        registry.add("LEXVAY_OIDC_CLIENT_SECRET", () -> "test-secret");
        // Discovery runs against the loopback provider; the endpoints come from its metadata.
        registry.add("LEXVAY_OIDC_ISSUER_URI", IDP::baseUrl);
    }

    @AfterAll
    static void stopIdentityProvider() {
        IDP.close();
    }

    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired AuditRepository audit;

    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        t = tenants.create("oidc");
    }

    /** Runs the browser's part of the flow and returns the result of the provider callback. */
    private MvcTestResult signIn(MockHttpSession session, Map<String, Object> claims) throws Exception {
        var start = mvc.perform(get("/oauth2/authorization/lexvay").session(session));
        assertThat(start).hasStatus(302);
        var query = UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build().getQueryParams();
        IDP.issueIdToken(param(query, "nonce"), claims);
        return mvc.perform(get("/login/oauth2/code/lexvay").param("code", "authorization-code")
                .param("state", param(query, "state")).session(session));
    }

    /** Query parameters of the provider redirect, percent-decoded the way the provider would read them. */
    private static String param(org.springframework.util.MultiValueMap<String, String> query, String name) {
        return org.springframework.web.util.UriUtils.decode(query.getFirst(name), java.nio.charset.StandardCharsets.UTF_8);
    }

    private JsonNode me(MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/api/v1/auth/me").session(session));
        assertThat(result).hasStatus(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void theAuthorizationRequestAsksTheProviderForOpenidEmailAndProfile() {
        var result = mvc.get().uri("/oauth2/authorization/lexvay").exchange();
        assertThat(result).hasStatus(302);
        var redirect = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build();
        assertThat(redirect.toUriString()).startsWith(IDP.baseUrl() + "/authorize?");
        assertThat(redirect.getQueryParams().getFirst("client_id")).isEqualTo(FakeIdentityProvider.CLIENT_ID);
        assertThat(redirect.getQueryParams().getFirst("response_type")).isEqualTo("code");
        assertThat(redirect.getQueryParams().getFirst("scope")).isEqualTo("openid%20email%20profile");
        assertThat(redirect.getQueryParams().getFirst("state")).isNotBlank();
        assertThat(redirect.getQueryParams().getFirst("nonce")).isNotBlank();
    }

    @Test
    void theRoleComesFromTheLexvayDatabaseAndNeverFromTheToken() throws Exception {
        var session = new MockHttpSession();
        var result = signIn(session, Map.of("email", t.reviewer().email(), "email_verified", true,
                "roles", List.of("ADMIN", "SIGNER"), "role", "ADMIN", "groups", List.of("lexvay-admins")));
        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/");

        JsonNode user = me(session);
        assertThat(user.get("id").asString()).isEqualTo(t.reviewer().id().toString());
        assertThat(user.get("tenantId").asString()).isEqualTo(t.tenantId().toString());
        assertThat(user.get("role").asString()).isEqualTo("REVIEWER");

        var asAdmin = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/programs")
                .session(session).with(csrf()).contentType("application/json")
                .content("{\"name\":\"P\",\"licenseType\":\"L\",\"catalogYear\":\"2026-27\",\"jurisdictions\":[\"CA\"]}"));
        assertThat(asAdmin).as("a reviewer cannot create programs whatever the token says").hasStatus(403);
    }

    @Test
    void theSessionPrincipalIsTheSameLexvayUserThatFormLoginProduces() throws Exception {
        var session = new MockHttpSession();
        signIn(session, Map.of("email", t.contributor().email()));
        var context = (org.springframework.security.core.context.SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(LexvayUser.class);
        assertThat(((LexvayUser) context.getAuthentication().getPrincipal()).id()).isEqualTo(t.contributor().id());
        assertThat(context.getAuthentication().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CONTRIBUTOR");
    }

    @Test
    void tenantBindingAndAuditWorkAsTheyDoForFormLogin() throws Exception {
        var session = new MockHttpSession();
        signIn(session, Map.of("email", t.contributor().email(), "email_verified", true));

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(t.contributor().id()));
        var login = entries.stream().filter(e -> e.action().equals("LOGIN_SUCCEEDED")).findFirst().orElseThrow();
        assertThat(login.userId()).isEqualTo(t.contributor().id());
        assertThat(login.detail()).containsEntry("method", "oidc").containsEntry("email", t.contributor().email());

        var programs = mvc.perform(get("/api/v1/programs").session(session));
        assertThat(programs).hasStatus(200);
    }

    @Test
    void emailMatchingIgnoresCase() throws Exception {
        var session = new MockHttpSession();
        assertThat(signIn(session, Map.of("email", t.signer().email().toUpperCase()))).hasStatus(302);
        assertThat(me(session).get("id").asString()).isEqualTo(t.signer().id().toString());
    }

    @Test
    void anAbsentEmailVerifiedClaimIsAccepted() throws Exception {
        var session = new MockHttpSession();
        assertThat(signIn(session, Map.of("email", t.signer().email()))).hasStatus(302);
    }

    /** MockMvc sessions are not container sessions, so the id change is also checked over a real connection. */
    @Test
    void theSessionIdChangesAtOidcLoginOverARealConnection() throws Exception {
        var client = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        java.util.function.BiFunction<String, String, java.net.http.HttpResponse<String>> get = (uri, cookie) -> {
            try {
                var builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + uri));
                if (cookie != null) {
                    builder.header("Cookie", cookie);
                }
                return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            } catch (java.io.IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        };
        var start = get.apply("/oauth2/authorization/lexvay", null);
        String planted = start.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        var query = UriComponentsBuilder.fromUriString(start.headers().firstValue("Location").orElseThrow()).build().getQueryParams();
        IDP.issueIdToken(param(query, "nonce"), Map.of("email", t.signer().email(), "email_verified", true));

        var callback = get.apply("/login/oauth2/code/lexvay?code=x&state=" + query.getFirst("state"), planted);
        assertThat(callback.statusCode()).isEqualTo(302);
        String issued = callback.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.split(";")[0]).findFirst().orElse(null);

        assertThat(issued).as("a new session cookie at login").isNotNull().isNotEqualTo(planted);
        assertThat(get.apply("/api/v1/auth/me", issued).statusCode()).isEqualTo(200);
        assertThat(get.apply("/api/v1/auth/me", planted).statusCode()).as("planted session").isEqualTo(401);
    }

    @Test
    void anUnverifiedEmailGets403AndNoSession() throws Exception {
        var session = new MockHttpSession();
        var result = signIn(session, Map.of("email", t.admin().email(), "email_verified", false));
        assertNoAccount(result);
        assertThat(mvc.perform(get("/api/v1/auth/me").session(session))).hasStatus(401);
    }

    @Test
    void anEmailVerifiedClaimSentAsTheStringFalseIsUnverified() throws Exception {
        assertNoAccount(signIn(new MockHttpSession(), Map.of("email", t.admin().email(), "email_verified", "false")));
    }

    @Test
    void anIdentityWithoutALexvayAccountGets403() throws Exception {
        var result = signIn(new MockHttpSession(), Map.of("email", "stranger-" + UUID.randomUUID() + "@nowhere.test", "email_verified", true));
        assertNoAccount(result);
    }

    @Test
    void anIdTokenWithoutAnEmailGets403() throws Exception {
        assertNoAccount(signIn(new MockHttpSession(), Map.of("name", "No Email")));
    }

    @Test
    void anEmailWithAControlCharacterGets403NotAServerError() throws Exception {
        assertNoAccount(signIn(new MockHttpSession(), Map.of("email", "a\u0000b@x.test", "email_verified", true)));
    }

    private void assertNoAccount(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(403);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
        JsonNode problem = json.readTree(result.getResponse().getContentAsString());
        assertThat(problem.get("detail").asString()).isEqualTo("No Lexvay account for this identity");
        assertThat(problem.get("status").asInt()).isEqualTo(403);
    }

    @Test
    void aCallbackWithAnUnknownStateIsRefused() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(get("/oauth2/authorization/lexvay").session(session));
        var result = mvc.perform(get("/login/oauth2/code/lexvay").param("code", "x").param("state", "forged").session(session));
        assertThat(result).hasStatus(401);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
    }

    @Test
    void anIdTokenForAnotherNonceIsRefused() throws Exception {
        var session = new MockHttpSession();
        var start = mvc.perform(get("/oauth2/authorization/lexvay").session(session));
        var query = UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build().getQueryParams();
        IDP.issueIdToken("another-nonce", Map.of("email", t.admin().email(), "email_verified", true));
        var result = mvc.perform(get("/login/oauth2/code/lexvay").param("code", "x").param("state", param(query, "state")).session(session));
        assertThat(result).hasStatus(401);
        assertThat(mvc.perform(get("/api/v1/auth/me").session(session))).hasStatus(401);
    }

    @Test
    void anIdTokenFromAnotherIssuerIsRefused() throws Exception {
        var session = new MockHttpSession();
        var start = mvc.perform(get("/oauth2/authorization/lexvay").session(session));
        var query = UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build().getQueryParams();
        IDP.issueIdToken("https://evil.example", param(query, "nonce"), Map.of("email", t.admin().email(), "email_verified", true));
        var result = mvc.perform(get("/login/oauth2/code/lexvay").param("code", "x").param("state", param(query, "state")).session(session));
        assertThat(result).hasStatus(401);
        assertThat(mvc.perform(get("/api/v1/auth/me").session(session))).hasStatus(401);
    }

    @Test
    void anApiRequestWithoutASessionGets401NotARedirect() {
        var result = mvc.get().uri("/api/v1/programs").exchange();
        assertThat(result).hasStatus(401);
        assertThat(result.getResponse().getHeader("Location")).isNull();
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
    }

    @Test
    void formLoginStillWorksWhenOidcIsEnabled() {
        var result = mvc.post().uri("/api/v1/auth/login").with(csrf())
                .param("username", t.signer().email()).param("password", TestTenants.PASSWORD).exchange();
        assertThat(result).hasStatus(200);
    }
}
