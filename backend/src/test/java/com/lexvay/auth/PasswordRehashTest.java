package com.lexvay.auth;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.audit.AuditEntry;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** D-401: a successful sign-in with an old hash replaces it with the current one, once, without ending any session. */
class PasswordRehashTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired AuditRepository audit;
    @Autowired JdbcClient jdbc;
    @Autowired LexvayUserDetailsService details;

    TestTenants.TenantWithUsers t;
    LexvayUser user;

    @BeforeEach
    void setUp() {
        t = tenants.create("rehash");
        user = t.reviewer();
    }

    private void storeHash(String hash) {
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("UPDATE app_user SET password_hash = :hash WHERE id = :id")
                .param("hash", hash).param("id", user.id()).update());
    }

    private String storedHash() {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT password_hash FROM app_user WHERE id = :id")
                .param("id", user.id()).query(String.class).single());
    }

    private int storedStamp() {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT security_stamp FROM app_user WHERE id = :id")
                .param("id", user.id()).query(Integer.class).single());
    }

    private MvcTestResult login(String password) {
        return mvc.post().uri("/api/v1/auth/login").with(csrf())
                .param("username", user.email()).param("password", password).exchange();
    }

    private List<AuditEntry> rehashEntries() {
        return TenantContext.runAs(t.tenantId(), () -> audit.forEntity(user.id())).stream()
                .filter(e -> e.action().equals("PASSWORD_REHASHED")).toList();
    }

    @Test
    void newUsersGetArgon2idHashes() {
        assertThat(storedHash()).startsWith("{argon2}$argon2id$");
    }

    @Test
    void aPrefixedBcryptHashIsReplacedAtTheNextSignInAndAuditedOnce() {
        storeHash("{bcrypt}" + new BCryptPasswordEncoder().encode(TestTenants.PASSWORD));
        int stamp = storedStamp();

        assertThat(login(TestTenants.PASSWORD)).hasStatus(200);

        String hash = storedHash();
        assertThat(hash).startsWith("{argon2}$argon2id$").doesNotContain(TestTenants.PASSWORD);
        assertThat(storedStamp()).as("no session of the user ends").isEqualTo(stamp);
        assertThat(rehashEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.userId()).as("the person is the actor").isEqualTo(user.id());
            assertThat(entry.entityType()).isEqualTo("app_user");
            assertThat(entry.entityId()).isEqualTo(user.id());
            assertThat(entry.detail()).as("algorithm names only, no secret").containsOnly(
                    Map.entry("from", "bcrypt"), Map.entry("to", "argon2"));
        });
        String everything = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT string_agg(detail::text || action, ' ') FROM audit_log").query(String.class).single());
        assertThat(everything).doesNotContain(hash).doesNotContain(TestTenants.PASSWORD);

        assertThat(login(TestTenants.PASSWORD)).as("the new hash verifies").hasStatus(200);
        assertThat(storedHash()).as("a current hash is left alone").isEqualTo(hash);
        assertThat(rehashEntries()).as("and nothing more is audited").hasSize(1);
    }

    @Test
    void aBcryptHashWithoutAPrefixIsReplacedToo() {
        storeHash(new BCryptPasswordEncoder().encode(TestTenants.PASSWORD));

        assertThat(login(TestTenants.PASSWORD)).hasStatus(200);

        assertThat(storedHash()).startsWith("{argon2}$argon2id$");
        assertThat(rehashEntries()).singleElement().satisfies(entry ->
                assertThat(entry.detail()).containsEntry("from", "bcrypt").containsEntry("to", "argon2"));
    }

    @Test
    void aWrongPasswordChangesNothing() {
        String old = "{bcrypt}" + new BCryptPasswordEncoder().encode(TestTenants.PASSWORD);
        storeHash(old);

        assertThat(login("not-the-password")).hasStatus(401);

        assertThat(storedHash()).isEqualTo(old);
        assertThat(rehashEntries()).isEmpty();
    }

    @Test
    void aPasswordSetWhileTheSignInWasRunningIsNotOverwritten() {
        String old = "{bcrypt}" + new BCryptPasswordEncoder().encode(TestTenants.PASSWORD);
        storeHash(old);
        LexvayUser loaded = (LexvayUser) details.loadUserByUsername(user.email());
        String chosenMeanwhile = "{argon2}$argon2id$v=19$m=32768,t=4,p=1$chosenMeanwhile";
        storeHash(chosenMeanwhile);

        var returned = (LexvayUser) details.updatePassword(loaded, "{argon2}$argon2id$v=19$m=32768,t=4,p=1$stale");

        assertThat(returned.passwordHash()).as("the caller still sees the hash it loaded").isEqualTo(old);
        assertThat(storedHash()).isEqualTo(chosenMeanwhile);
        assertThat(rehashEntries()).isEmpty();
    }
}
