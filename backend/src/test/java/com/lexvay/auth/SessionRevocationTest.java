package com.lexvay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestHttpClient;
import com.lexvay.common.TestTenants;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

/**
 * A session ends when its user's access changes (D-374), over real HTTP: MockMvc has no container session to
 * invalidate. The change is made by an administrator in a session of their own, on the same instance, so it takes
 * effect on the very next request; a change from another instance is covered with a fake clock in
 * {@link UserStampCheckTest}.
 */
class SessionRevocationTest extends AbstractDatabaseTest {

    private static final String CHANGED = "Your access changed. Sign in again.";

    @LocalServerPort int port;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;

    TestTenants.TenantWithUsers t;
    TestHttpClient admin;

    @BeforeEach
    void setUp() {
        t = tenants.create("revocation");
        admin = signedIn(t.admin().email());
    }

    private TestHttpClient signedIn(String email) {
        TestHttpClient client = new TestHttpClient(port);
        client.login(email, TestTenants.PASSWORD);
        return client;
    }

    private String body(Map<String, Object> fields) {
        return json.writeValueAsString(fields);
    }

    private void assertEnded(TestHttpClient client) {
        var refused = client.get("/api/v1/programs");
        assertThat(refused.status()).isEqualTo(401);
        assertThat(json.readTree(refused.body()).get("detail").asString()).isEqualTo(CHANGED);
        var session = json.readTree(client.get("/api/v1/auth/session").body());
        assertThat(session.get("authenticated").asBoolean()).as("the session itself is gone").isFalse();
    }

    @Test
    void deactivatingAUserEndsTheirSessionOnTheNextRequest() {
        TestHttpClient reviewer = signedIn(t.reviewer().email());
        assertThat(reviewer.get("/api/v1/programs").status()).isEqualTo(200);

        var off = admin.putJson("/api/v1/users/" + t.reviewer().id() + "/active", body(Map.of("active", false)));
        assertThat(off.status()).isEqualTo(200);

        assertEnded(reviewer);
        assertThat(admin.get("/api/v1/programs").status()).as("the administrator's own session is untouched").isEqualTo(200);
    }

    @Test
    void aRoleChangeEndsTheSessionAndTheNextSignInCarriesTheNewRole() {
        TestHttpClient signer = signedIn(t.signer().email());
        assertThat(json.readTree(signer.get("/api/v1/auth/me").body()).get("role").asString()).isEqualTo("SIGNER");

        var changed = admin.putJson("/api/v1/users/" + t.signer().id() + "/role", body(Map.of("role", "CONTRIBUTOR")));
        assertThat(changed.status()).isEqualTo(200);
        assertEnded(signer);

        TestHttpClient again = signedIn(t.signer().email());
        assertThat(json.readTree(again.get("/api/v1/auth/me").body()).get("role").asString()).isEqualTo("CONTRIBUTOR");
    }

    @Test
    void aReactivatedUserSignsInAgain() {
        TestHttpClient contributor = signedIn(t.contributor().email());
        admin.putJson("/api/v1/users/" + t.contributor().id() + "/active", body(Map.of("active", false)));
        assertEnded(contributor);

        TestHttpClient refused = new TestHttpClient(port);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> refused.login(t.contributor().email(), TestTenants.PASSWORD)))
                .hasMessageContaining("401");

        admin.putJson("/api/v1/users/" + t.contributor().id() + "/active", body(Map.of("active", true)));
        assertThat(signedIn(t.contributor().email()).get("/api/v1/programs").status()).isEqualTo(200);
    }

    @Test
    void choosingANewPasswordEndsTheSessionsTheUserAlreadyHas() {
        TestHttpClient reviewer = signedIn(t.reviewer().email());
        var issued = admin.postJson("/api/v1/users/" + t.reviewer().id() + "/setup-code", "{}");
        assertThat(issued.status()).isEqualTo(201);
        assertThat(reviewer.get("/api/v1/programs").status()).as("a code alone changes nothing").isEqualTo(200);

        TestHttpClient anonymous = new TestHttpClient(port);
        anonymous.get("/api/v1/auth/session");
        String code = json.readTree(issued.body()).get("setupCode").asString();
        var set = anonymous.postJson("/api/v1/auth/setup", body(Map.of("code", code, "password", "a-different-password-" + UUID.randomUUID().toString().substring(0, 4))));
        assertThat(set.status()).isEqualTo(204);

        assertEnded(reviewer);
    }
}
