package com.lexvay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.audit.AuditEntry;
import com.lexvay.audit.AuditFilter;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
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
 * Product rule 9 for signing out (D-410), over a real HTTP session: the entry names the person who signed out as its
 * actor and their account as its record, in their tenant. Signing out without a session records nothing.
 */
class LogoutAuditTest extends AbstractDatabaseTest {

    private static final String CSRF = "csrf-token-value";
    private static final int MANY = 1000;

    @Autowired TestTenants tenants;
    @Autowired AuditRepository audit;
    @LocalServerPort int port;

    RestClient client;
    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        t = tenants.create("logout");
        client = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private String signIn(String email) {
        ResponseEntity<String> response = client.post().uri(SecurityConfig.LOGIN_URL)
                .header("Cookie", "XSRF-TOKEN=" + CSRF).header("X-XSRF-TOKEN", CSRF)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=" + email + "&password=" + TestTenants.PASSWORD)
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getHeaders().getOrDefault("Set-Cookie", List.of()).stream()
                .filter(c -> c.startsWith("JSESSIONID=")).map(c -> c.substring("JSESSIONID=".length()).split(";")[0])
                .findFirst().orElseThrow();
    }

    private int signOut(String sessionId) {
        return client.post().uri(SecurityConfig.LOGOUT_URL)
                .header("Cookie", "XSRF-TOKEN=" + CSRF + (sessionId == null ? "" : "; JSESSIONID=" + sessionId))
                .header("X-XSRF-TOKEN", CSRF)
                .retrieve().toEntity(String.class).getStatusCode().value();
    }

    private List<AuditEntry> entries(String action) {
        var filter = new AuditFilter(action, null, null, null, null, null);
        return TenantContext.runAs(t.tenantId(), () -> audit.newestFirst(filter, MANY, 0));
    }

    @Test
    void signingOutRecordsThePersonAsActorOnTheirOwnAccount() {
        String session = signIn(t.reviewer().email());

        assertThat(signOut(session)).isEqualTo(204);

        assertThat(entries("LOGOUT")).singleElement().satisfies(entry -> {
            assertThat(entry.userId()).as("actor").isEqualTo(t.reviewer().id());
            assertThat(entry.entityType()).isEqualTo("app_user");
            assertThat(entry.entityId()).isEqualTo(t.reviewer().id());
        });
    }

    @Test
    void eachPersonIsTheActorOfTheirOwnEntryOnly() {
        String reviewer = signIn(t.reviewer().email());
        String signer = signIn(t.signer().email());

        assertThat(signOut(signer)).isEqualTo(204);
        assertThat(entries("LOGOUT")).singleElement()
                .satisfies(entry -> assertThat(entry.userId()).isEqualTo(t.signer().id()));

        assertThat(signOut(reviewer)).isEqualTo(204);
        assertThat(entries("LOGOUT")).extracting(AuditEntry::userId)
                .containsExactlyInAnyOrder(t.signer().id(), t.reviewer().id());
    }

    @Test
    void signingOutWithoutASessionRecordsNothing() {
        assertThat(signOut(null)).isEqualTo(204);
        assertThat(signOut("not-a-session")).isEqualTo(204);

        assertThat(entries("LOGOUT")).isEmpty();
    }
}
