package com.lexvay.tenant;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.audit.AuditEntry;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** F5a: a person with a setup code chooses a password, once (D-372, D-373). */
class PasswordSetupApiTest extends AbstractDatabaseTest {

    private static final String NOT_VALID = "This setup code is not valid";
    private static final String GOOD_PASSWORD = "a-good-long-password";

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired AuditRepository audit;
    @Autowired JdbcClient jdbc;

    TestTenants.TenantWithUsers t;
    String email;
    UUID userId;
    String code;

    @BeforeEach
    void setUp() throws Exception {
        t = tenants.create("setup");
        email = "someone.long." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        var created = mvc.post().uri("/api/v1/users").with(user(t.admin())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "displayName", "Setup Person", "role", "REVIEWER"))).exchange();
        assertThat(created).hasStatus(201);
        JsonNode body = body(created);
        userId = UUID.fromString(body.get("user").get("id").asString());
        code = body.get("setupCode").asString();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private MvcTestResult setup(String withCode, String password) {
        return mvc.post().uri("/api/v1/auth/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", withCode, "password", password))).exchange();
    }

    private MvcTestResult login(String password) {
        return mvc.post().uri("/api/v1/auth/login").with(csrf()).param("username", email).param("password", password).exchange();
    }

    private <T> T query(String sql, Class<T> type, Object... params) {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql(sql).params(params).query(type).single());
    }

    private void assertNotValid(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(400);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
        assertThat(body(result).get("detail").asString()).isEqualTo(NOT_VALID);
    }

    @Test
    void aValidCodeSetsThePasswordOnceAndIsAuditedAsThePerson() throws Exception {
        assertThat(login(GOOD_PASSWORD)).as("no password before the code is used").hasStatus(401);
        int stamp = query("SELECT security_stamp FROM app_user WHERE id = ?", Integer.class, userId);

        var done = setup(code, GOOD_PASSWORD);
        assertThat(done).hasStatus(204);
        assertThat(done.getResponse().getContentAsString()).as("nobody is signed in by it").isEmpty();
        assertThat(done.getResponse().getHeader("Set-Cookie")).isNull();

        assertThat(login(GOOD_PASSWORD)).hasStatus(200);
        assertThat(query("SELECT security_stamp FROM app_user WHERE id = ?", Integer.class, userId)).isEqualTo(stamp + 1);
        assertThat(query("SELECT password_hash FROM app_user WHERE id = ?", String.class, userId))
                .startsWith("{argon2}$argon2id$").doesNotContain(GOOD_PASSWORD);
        assertThat(query("SELECT used_at IS NOT NULL FROM user_setup_token WHERE user_id = ?", Boolean.class, userId)).isTrue();

        AuditEntry entry = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(userId)).stream()
                .filter(e -> e.action().equals("PASSWORD_SET")).findFirst().orElseThrow();
        assertThat(entry.userId()).as("the person is the actor").isEqualTo(userId);
        assertThat(entry.entityType()).isEqualTo("app_user");
        String everything = query("SELECT string_agg(detail::text || action, ' ') FROM audit_log", String.class);
        assertThat(everything).doesNotContain(code).doesNotContain(GOOD_PASSWORD);

        assertNotValid(setup(code, "another-good-password"));
        assertThat(login("another-good-password")).as("a used code changes nothing").hasStatus(401);
    }

    @Test
    void unknownUsedAndExpiredCodesAreRefusedWithTheSameMessage() throws Exception {
        assertNotValid(setup("not-a-code-that-was-ever-issued", GOOD_PASSWORD));

        assertThat(setup(code, GOOD_PASSWORD)).hasStatus(204);
        assertNotValid(setup(code, GOOD_PASSWORD));

        String expired = "expired-code-" + UUID.randomUUID();
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("INSERT INTO user_setup_token (tenant_id, user_id, token_hash, expires_at, created_at) "
                        + "VALUES (:tenant, :user, encode(sha256(convert_to(:code, 'UTF8')), 'hex'), now() - interval '1 hour', now() - interval '73 hours')")
                .param("tenant", t.tenantId()).param("user", userId).param("code", expired).update());
        assertNotValid(setup(expired, GOOD_PASSWORD));
    }

    @Test
    void aCodeThatWasReplacedByANewOneNoLongerWorks() throws Exception {
        var reissued = mvc.post().uri("/api/v1/users/" + userId + "/setup-code").with(user(t.admin())).with(csrf()).exchange();
        String newer = body(reissued).get("setupCode").asString();

        assertNotValid(setup(code, GOOD_PASSWORD));
        assertThat(setup(newer, GOOD_PASSWORD)).hasStatus(204);
    }

    @Test
    void aBadPasswordNamesTheRuleAndDoesNotUseUpTheCode() throws Exception {
        var tooShort = setup(code, "elevenchars");
        assertThat(tooShort).hasStatus(400);
        assertThat(body(tooShort).get("detail").asString()).isEqualTo("Password must have at least 12 characters");

        var tooLong = setup(code, "p".repeat(129));
        assertThat(tooLong).hasStatus(400);
        assertThat(body(tooLong).get("detail").asString()).isEqualTo("Password must have at most 128 characters");
        assertThat(setup(code, "é".repeat(129))).as("characters are counted, not bytes").hasStatus(400);
        assertThat(setup(code, "😀".repeat(129))).as("a character outside the basic plane counts once").hasStatus(400);

        for (String same : List.of(email, email.toUpperCase())) {
            var asEmail = setup(code, same);
            assertThat(asEmail).hasStatus(400);
            assertThat(body(asEmail).get("detail").asString()).isEqualTo("Password must not be the same as the email");
        }

        assertThat(setup(code, "p".repeat(128))).as("128 characters are accepted").hasStatus(204);
    }

    @Test
    void aLongPasswordIsStoredWholeAndSignsInOnlyWithAllOfIt() {
        String longest = "p".repeat(127) + "q";
        assertThat(setup(code, longest)).hasStatus(204);

        assertThat(login(longest)).hasStatus(200);
        assertThat(login("p".repeat(128))).as("the end of the password counts, there is no cut at 72 bytes").hasStatus(401);
        assertThat(login("p".repeat(72))).hasStatus(401);
    }

    @Test
    void aPasswordOf128MultiByteCharactersIsAcceptedAndSignsIn() {
        String multiByte = "é日😀".repeat(42) + "éa"; // 128 characters, 2, 3 and 4 bytes: 381 bytes in all
        assertThat(multiByte.codePointCount(0, multiByte.length())).isEqualTo(128);
        assertThat(setup(code, multiByte)).hasStatus(204);

        assertThat(login(multiByte)).hasStatus(200);
        assertThat(login(multiByte.substring(0, multiByte.length() - 1) + "b")).hasStatus(401);
    }

    @Test
    void aCodeOfAnotherTenantsUserIsHonouredOnlyForThatUser() throws Exception {
        var other = tenants.create("setup-other");
        var created = mvc.post().uri("/api/v1/users").with(user(other.admin())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "else." + UUID.randomUUID().toString().substring(0, 8) + "@example.test",
                        "displayName", "Elsewhere", "role", "SIGNER"))).exchange();
        UUID otherUser = UUID.fromString(body(created).get("user").get("id").asString());
        assertThat(setup(body(created).get("setupCode").asString(), GOOD_PASSWORD)).hasStatus(204);

        assertThat(TenantContext.runAs(other.tenantId(), () -> jdbc.sql("SELECT password_hash IS NOT NULL FROM app_user WHERE id = :id")
                .param("id", otherUser).query(Boolean.class).single())).isTrue();
        assertThat(query("SELECT password_hash IS NULL FROM app_user WHERE id = ?", Boolean.class, userId)).isTrue();
    }

    @Test
    void theRequestNeedsTheCsrfToken() {
        var refused = mvc.post().uri("/api/v1/auth/setup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"password\":\"" + GOOD_PASSWORD + "\"}").exchange();
        assertThat(refused).hasStatus(403);
    }

    @Test
    void twoRequestsWithTheSameCodeAtOnceSetThePasswordOnlyOnce() throws Exception {
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> {
                barrier.await();
                return setup(code, GOOD_PASSWORD).getResponse().getStatus();
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(204, 400);
        } finally {
            pool.shutdownNow();
        }
        assertThat(query("SELECT count(*) FROM audit_log WHERE action = 'PASSWORD_SET' AND entity_id = ?", Long.class, userId)).isEqualTo(1L);
    }
}
