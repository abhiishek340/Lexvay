package com.lexvay.tenant;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.audit.AuditEntry;
import com.lexvay.audit.AuditRepository;
import com.lexvay.auth.LexvayUser;
import com.lexvay.auth.Role;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** F5a: an administrator adds users, hands out setup codes, changes roles and withdraws access (D-370 to D-376). */
class UserAdminApiTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired AuditRepository audit;
    @Autowired JdbcClient jdbc;

    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        t = tenants.create("useradmin");
    }

    private MvcTestResult post(LexvayUser as, String uri, Object body) throws Exception {
        var request = mvc.post().uri(uri).with(user(as)).with(csrf());
        return body == null ? request.exchange()
                : request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
    }

    private MvcTestResult put(LexvayUser as, String uri, Object body) throws Exception {
        return mvc.put().uri(uri).with(user(as)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)).exchange();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode create(String email, String name, Role role) throws Exception {
        var result = post(t.admin(), "/api/v1/users", Map.of("email", email, "displayName", name, "role", role.name()));
        assertThat(result).hasStatus(201);
        return body(result);
    }

    private static String freshEmail() {
        return "new." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    private List<AuditEntry> auditOf(UUID userId) {
        return TenantContext.runAs(t.tenantId(), () -> audit.forEntity(userId));
    }

    private AuditEntry latest(UUID userId, String action) {
        return auditOf(userId).stream().filter(e -> e.action().equals(action)).findFirst().orElseThrow();
    }

    private <T> T tenantQuery(String sql, Class<T> type, Object... params) {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql(sql).params(params).query(type).single());
    }

    private int stampOf(UUID userId) {
        return tenantQuery("SELECT security_stamp FROM app_user WHERE id = ?", Integer.class, userId);
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    // --- create -------------------------------------------------------------------------------------------

    @Test
    void anAdministratorAddsAUserWhoHasNoPasswordYetAndGetsTheOnlyCopyOfTheCode() throws Exception {
        String email = freshEmail();
        JsonNode created = create(email, "  New Colleague ", Role.REVIEWER);

        JsonNode user = created.get("user");
        assertThat(user.get("email").asString()).isEqualTo(email);
        assertThat(user.get("displayName").asString()).isEqualTo("New Colleague");
        assertThat(user.get("role").asString()).isEqualTo("REVIEWER");
        assertThat(user.get("active").asBoolean()).isTrue();
        assertThat(user.get("passwordSet").asBoolean()).isFalse();
        assertThat(user.has("passwordHash")).isFalse();

        String code = created.get("setupCode").asString();
        assertThat(code).matches("[A-Za-z0-9_-]{43}"); // 32 bytes, unpadded base64url
        OffsetDateTime expiresAt = OffsetDateTime.parse(created.get("expiresAt").asString());
        assertThat(Duration.between(OffsetDateTime.now(), expiresAt)).isBetween(Duration.ofHours(72).minusMinutes(1), Duration.ofHours(72));

        UUID userId = UUID.fromString(user.get("id").asString());
        assertThat(tenantQuery("SELECT token_hash FROM user_setup_token WHERE user_id = ?", String.class, userId))
                .as("only the hash is stored").isEqualTo(sha256(code));
        assertThat(tenantQuery("SELECT count(*) FROM user_setup_token WHERE token_hash = ?", Long.class, code)).isZero();
        assertThat(tenantQuery("SELECT password_hash IS NULL FROM app_user WHERE id = ?", Boolean.class, userId)).isTrue();
        assertThat(tenantQuery("SELECT tenant_id FROM app_user WHERE id = ?", UUID.class, userId)).isEqualTo(t.tenantId());
    }

    @Test
    void creatingAUserIsAuditedWithoutTheCode() throws Exception {
        String email = freshEmail();
        JsonNode created = create(email, "Audited Colleague", Role.SIGNER);
        UUID userId = UUID.fromString(created.get("user").get("id").asString());

        AuditEntry entry = latest(userId, "USER_CREATED");
        assertThat(entry.userId()).isEqualTo(t.admin().id());
        assertThat(entry.entityType()).isEqualTo("app_user");
        assertThat(entry.detail()).containsOnly(Map.entry("email", email), Map.entry("role", "SIGNER"));

        String everythingAudited = TenantContext.runAs(t.tenantId(),
                () -> jdbc.sql("SELECT string_agg(detail::text || action, ' ') FROM audit_log").query(String.class).single());
        assertThat(everythingAudited).doesNotContain(created.get("setupCode").asString());
    }

    @Test
    void theEmailIsLowerCasedAndUniqueAcrossAllTenants() throws Exception {
        String email = freshEmail();
        JsonNode created = create(email.toUpperCase(), "Upper Case", Role.CONTRIBUTOR);
        assertThat(created.get("user").get("email").asString()).isEqualTo(email);

        var again = post(t.admin(), "/api/v1/users", Map.of("email", email, "displayName", "Twice", "role", "CONTRIBUTOR"));
        assertThat(again).hasStatus(409);
        assertThat(body(again).get("detail").asString()).isEqualTo("An account with this email exists");

        var other = tenants.create("useradmin-other");
        var inAnotherTenant = post(other.admin(), "/api/v1/users", Map.of("email", email, "displayName", "Elsewhere", "role", "CONTRIBUTOR"));
        assertThat(inAnotherTenant).as("another tenant's address is taken as well").hasStatus(409);
        assertThat(body(inAnotherTenant).get("detail").asString()).isEqualTo("An account with this email exists");
    }

    @Test
    void onlyAnAdministratorMayChangeUsers() throws Exception {
        UUID target = t.contributor().id();
        for (LexvayUser who : List.of(t.contributor(), t.reviewer(), t.signer())) {
            assertThat(post(who, "/api/v1/users", Map.of("email", freshEmail(), "displayName", "X", "role", "CONTRIBUTOR"))).hasStatus(403);
            assertThat(post(who, "/api/v1/users/" + target + "/setup-code", null)).hasStatus(403);
            assertThat(put(who, "/api/v1/users/" + target + "/role", Map.of("role", "SIGNER"))).hasStatus(403);
            assertThat(put(who, "/api/v1/users/" + target + "/active", Map.of("active", false))).hasStatus(403);
        }
        assertThat(mvc.post().uri("/api/v1/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.test\",\"displayName\":\"A\",\"role\":\"ADMIN\"}").exchange()).hasStatus(401);
        assertThat(stampOf(target)).isZero();
    }

    // --- setup codes --------------------------------------------------------------------------------------

    @Test
    void aNewCodeClosesTheEarlierOnesAndIsAudited() throws Exception {
        JsonNode created = create(freshEmail(), "Needs A New Code", Role.REVIEWER);
        UUID userId = UUID.fromString(created.get("user").get("id").asString());
        String first = created.get("setupCode").asString();

        var reissued = post(t.admin(), "/api/v1/users/" + userId + "/setup-code", null);
        assertThat(reissued).hasStatus(201);
        String second = body(reissued).get("setupCode").asString();
        assertThat(second).isNotEqualTo(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(body(reissued).get("user").get("id").asString()).isEqualTo(userId.toString());

        assertThat(tenantQuery("SELECT used_at IS NOT NULL FROM user_setup_token WHERE token_hash = ?", Boolean.class, sha256(first)))
                .as("the earlier code is closed").isTrue();
        assertThat(tenantQuery("SELECT used_at IS NULL FROM user_setup_token WHERE token_hash = ?", Boolean.class, sha256(second)))
                .as("the new code is open").isTrue();

        AuditEntry entry = latest(userId, "USER_SETUP_CODE_ISSUED");
        assertThat(entry.userId()).isEqualTo(t.admin().id());
        assertThat(entry.detail()).containsKeys("email", "expiresAt").doesNotContainValue(second).doesNotContainValue(first);
    }

    @Test
    void aCodeForAnUnknownOrForeignUserIsNotFound() throws Exception {
        assertThat(post(t.admin(), "/api/v1/users/" + UUID.randomUUID() + "/setup-code", null)).hasStatus(404);
        var other = tenants.create("useradmin-foreign");
        assertThat(post(t.admin(), "/api/v1/users/" + other.reviewer().id() + "/setup-code", null)).hasStatus(404);
        assertThat(put(t.admin(), "/api/v1/users/" + other.reviewer().id() + "/role", Map.of("role", "ADMIN"))).hasStatus(404);
        assertThat(put(t.admin(), "/api/v1/users/" + other.reviewer().id() + "/active", Map.of("active", false))).hasStatus(404);
    }

    // --- role and access ----------------------------------------------------------------------------------

    @Test
    void aRoleChangeIsAuditedWithTheOldAndNewRole() throws Exception {
        UUID target = t.reviewer().id();
        int before = stampOf(target);

        var changed = put(t.admin(), "/api/v1/users/" + target + "/role", Map.of("role", "SIGNER"));
        assertThat(changed).hasStatus(200);
        assertThat(body(changed).get("role").asString()).isEqualTo("SIGNER");
        assertThat(body(changed).get("active").asBoolean()).isTrue();

        assertThat(stampOf(target)).isEqualTo(before + 1);
        AuditEntry entry = latest(target, "USER_ROLE_CHANGED");
        assertThat(entry.userId()).isEqualTo(t.admin().id());
        assertThat(entry.detail()).containsEntry("from", "REVIEWER").containsEntry("to", "SIGNER").containsEntry("email", t.reviewer().email());
    }

    @Test
    void settingTheRoleAUserHasChangesNothing() throws Exception {
        UUID target = t.reviewer().id();
        int before = stampOf(target);
        assertThat(put(t.admin(), "/api/v1/users/" + target + "/role", Map.of("role", "REVIEWER"))).hasStatus(200);
        assertThat(put(t.admin(), "/api/v1/users/" + target + "/active", Map.of("active", true))).hasStatus(200);
        assertThat(stampOf(target)).as("no session is ended for nothing").isEqualTo(before);
        assertThat(auditOf(target)).extracting(AuditEntry::action).doesNotContain("USER_ROLE_CHANGED", "USER_REACTIVATED");
    }

    @Test
    void deactivationAndReactivationAreAudited() throws Exception {
        UUID target = t.contributor().id();
        int before = stampOf(target);

        var off = put(t.admin(), "/api/v1/users/" + target + "/active", Map.of("active", false));
        assertThat(off).hasStatus(200);
        assertThat(body(off).get("active").asBoolean()).isFalse();
        assertThat(stampOf(target)).isEqualTo(before + 1);
        assertThat(latest(target, "USER_DEACTIVATED").userId()).isEqualTo(t.admin().id());

        var on = put(t.admin(), "/api/v1/users/" + target + "/active", Map.of("active", true));
        assertThat(on).hasStatus(200);
        assertThat(body(on).get("active").asBoolean()).isTrue();
        assertThat(stampOf(target)).isEqualTo(before + 2);
        assertThat(latest(target, "USER_REACTIVATED").userId()).isEqualTo(t.admin().id());
    }

    @Test
    void aDeactivatedUserCannotSignInAndTheMessageDoesNotSayWhy() throws Exception {
        UUID target = t.signer().id();
        var before = mvc.post().uri("/api/v1/auth/login").with(csrf()).param("username", t.signer().email())
                .param("password", TestTenants.PASSWORD).exchange();
        assertThat(before).hasStatus(200);

        assertThat(put(t.admin(), "/api/v1/users/" + target + "/active", Map.of("active", false))).hasStatus(200);
        var refused = mvc.post().uri("/api/v1/auth/login").with(csrf()).param("username", t.signer().email())
                .param("password", TestTenants.PASSWORD).exchange();
        var wrongPassword = mvc.post().uri("/api/v1/auth/login").with(csrf()).param("username", t.signer().email())
                .param("password", "wrong").exchange();
        assertThat(refused).hasStatus(401);
        assertThat(body(refused).get("detail").asString()).isEqualTo("Email or password is incorrect")
                .isEqualTo(body(wrongPassword).get("detail").asString());

        assertThat(put(t.admin(), "/api/v1/users/" + target + "/active", Map.of("active", true))).hasStatus(200);
        assertThat(mvc.post().uri("/api/v1/auth/login").with(csrf()).param("username", t.signer().email())
                .param("password", TestTenants.PASSWORD).exchange()).hasStatus(200);
    }

    @Test
    void aUserWithoutAPasswordCannotSignIn() throws Exception {
        String email = freshEmail();
        create(email, "No Password Yet", Role.CONTRIBUTOR);
        var refused = mvc.post().uri("/api/v1/auth/login").with(csrf()).param("username", email).param("password", "anything-at-all-123").exchange();
        assertThat(refused).hasStatus(401);
        assertThat(body(refused).get("detail").asString()).isEqualTo("Email or password is incorrect");
    }

    @Test
    void anAdministratorCannotChangeOwnRoleOrDeactivateThemselves() throws Exception {
        UUID self = t.admin().id();
        var role = put(t.admin(), "/api/v1/users/" + self + "/role", Map.of("role", "REVIEWER"));
        assertThat(role).hasStatus(409);
        assertThat(body(role).get("detail").asString()).isEqualTo("You cannot change your own role");
        var active = put(t.admin(), "/api/v1/users/" + self + "/active", Map.of("active", false));
        assertThat(active).hasStatus(409);
        assertThat(body(active).get("detail").asString()).isEqualTo("You cannot deactivate your own account");
        assertThat(stampOf(self)).isZero();
        assertThat(tenantQuery("SELECT role FROM app_user WHERE id = ?", String.class, self)).isEqualTo("ADMIN");
    }

    /**
     * Another instance withdrew the caller's administrator role less than 15 seconds ago and this instance still
     * trusts the cached state (D-374). The caller then tries to remove the only administrator left.
     */
    @Test
    void theLastActiveAdministratorCannotBeRemovedWhateverTheCallerBelievesAboutThemselves() throws Exception {
        JsonNode second = create(freshEmail(), "Second Admin", Role.ADMIN);
        UUID secondId = UUID.fromString(second.get("user").get("id").asString());
        LexvayUser secondAdmin = new LexvayUser(secondId, t.tenantId(), second.get("user").get("email").asString(),
                "Second Admin", Role.ADMIN, null, 0);
        assertThat(mvc.get().uri("/api/v1/auth/me").with(user(secondAdmin)).exchange()).as("warms the cache").hasStatus(200);
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("UPDATE app_user SET role = 'REVIEWER', security_stamp = security_stamp + 1 WHERE id = :id")
                .param("id", secondId).update());

        var role = put(secondAdmin, "/api/v1/users/" + t.admin().id() + "/role", Map.of("role", "REVIEWER"));
        assertThat(role).hasStatus(409);
        assertThat(body(role).get("detail").asString()).isEqualTo("At least one active administrator is required");
        var active = put(secondAdmin, "/api/v1/users/" + t.admin().id() + "/active", Map.of("active", false));
        assertThat(active).hasStatus(409);
        assertThat(body(active).get("detail").asString()).isEqualTo("At least one active administrator is required");
        assertThat(tenantQuery("SELECT role || active FROM app_user WHERE id = ?", String.class, t.admin().id())).isEqualTo("ADMINtrue");
    }

    @Test
    void anAdministratorCanBeRemovedWhenAnotherOneRemains() throws Exception {
        JsonNode second = create(freshEmail(), "Second Admin", Role.ADMIN);
        UUID secondId = UUID.fromString(second.get("user").get("id").asString());
        assertThat(put(t.admin(), "/api/v1/users/" + secondId + "/role", Map.of("role", "SIGNER"))).hasStatus(200);
        assertThat(put(t.admin(), "/api/v1/users/" + secondId + "/role", Map.of("role", "ADMIN"))).hasStatus(200);
        assertThat(put(t.admin(), "/api/v1/users/" + secondId + "/active", Map.of("active", false))).hasStatus(200);
    }

    // --- reads --------------------------------------------------------------------------------------------

    @Test
    void administratorsSeeTheStateOfEveryUserAndOtherRolesKeepTodaysFields() throws Exception {
        create("zed." + UUID.randomUUID().toString().substring(0, 6) + "@example.test", "Zed Last", Role.CONTRIBUTOR);
        var adminList = mvc.get().uri("/api/v1/users").with(user(t.admin())).exchange();
        assertThat(adminList).hasStatus(200);
        List<JsonNode> users = new ArrayList<>();
        body(adminList).forEach(users::add);

        assertThat(users).extracting(u -> u.get("displayName").asString()).isSorted();
        assertThat(users).hasSize(5).allSatisfy(u -> assertThat(u.propertyNames())
                .containsExactlyInAnyOrder("id", "displayName", "role", "email", "active", "createdAt", "passwordSet"));
        assertThat(users).filteredOn(u -> u.get("displayName").asString().equals("Zed Last"))
                .singleElement().satisfies(u -> assertThat(u.get("passwordSet").asBoolean()).isFalse());
        assertThat(users).filteredOn(u -> u.get("id").asString().equals(t.signer().id().toString()))
                .singleElement().satisfies(u -> {
                    assertThat(u.get("passwordSet").asBoolean()).isTrue();
                    assertThat(u.get("active").asBoolean()).isTrue();
                });

        for (LexvayUser who : List.of(t.contributor(), t.reviewer(), t.signer())) {
            var others = mvc.get().uri("/api/v1/users").with(user(who)).exchange();
            assertThat(others).hasStatus(200);
            body(others).forEach(u -> assertThat(u.propertyNames()).containsExactlyInAnyOrder("id", "displayName", "role", "email"));
        }
    }

    @Test
    void aUserOfAnotherTenantIsNeverListed() throws Exception {
        var other = tenants.create("useradmin-listing");
        var list = mvc.get().uri("/api/v1/users").with(user(other.admin())).exchange();
        List<String> emails = new ArrayList<>();
        body(list).forEach(u -> emails.add(u.get("email").asString()));
        assertThat(emails).doesNotContain(t.admin().email(), t.reviewer().email());
    }
}
