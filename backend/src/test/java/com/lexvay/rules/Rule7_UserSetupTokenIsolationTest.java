package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.tenant.SetupTokenRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Product rule 7 for setup codes: another tenant, or no tenant, sees and changes nothing; the lookup flag only reads. */
class Rule7_UserSetupTokenIsolationTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired SetupTokenRepository repository;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate transaction;

    TestTenants.TenantWithUsers a;
    TestTenants.TenantWithUsers b;
    UUID tokenOfA;
    String hashOfA;

    @BeforeEach
    void setUp() {
        a = tenants.create("token-a");
        b = tenants.create("token-b");
        tokenOfA = UUID.randomUUID();
        hashOfA = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        TenantContext.runAs(a.tenantId(), () ->
                repository.insert(tokenOfA, a.tenantId(), a.reviewer().id(), hashOfA, OffsetDateTime.now().plusHours(72), a.admin().id()));
    }

    private long count() {
        return jdbc.sql("SELECT count(*) FROM user_setup_token").query(Long.class).single();
    }

    @Test
    void anotherTenantSeesNoCodes() {
        assertThat(TenantContext.runAs(b.tenantId(), this::count)).isZero();
        assertThat(TenantContext.runAs(a.tenantId(), this::count)).isEqualTo(1);
    }

    @Test
    void noTenantFailsClosed() {
        TenantContext.runWithoutTenant(() -> {
            assertThat(count()).isZero();
            assertThatThrownBy(() -> repository.insert(UUID.randomUUID(), a.tenantId(), a.reviewer().id(), "f".repeat(64),
                    OffsetDateTime.now().plusHours(1), null)).isInstanceOf(DataAccessException.class);
            return null;
        });
    }

    @Test
    void aTenantCannotWriteOrChangeAnotherTenantsCodes() {
        TenantContext.runAs(b.tenantId(), () -> {
            assertThatThrownBy(() -> repository.insert(UUID.randomUUID(), a.tenantId(), a.reviewer().id(), "e".repeat(64),
                    OffsetDateTime.now().plusHours(1), null)).isInstanceOf(DataAccessException.class);
            assertThat(repository.markUsed(tokenOfA)).isFalse();
            assertThat(repository.closeOpenCodes(a.reviewer().id())).isZero();
            return null;
        });
        assertThat(TenantContext.runAs(a.tenantId(), () ->
                jdbc.sql("SELECT used_at IS NULL FROM user_setup_token WHERE id = :id").param("id", tokenOfA).query(Boolean.class).single()))
                .isTrue();
    }

    @Test
    void theLookupFlagOpensTheCodeAndItsUserAndNothingElse() {
        var found = TenantContext.runWithoutTenant(() -> repository.findOpen(hashOfA)).orElseThrow();
        assertThat(found.id()).isEqualTo(tokenOfA);
        assertThat(found.tenantId()).isEqualTo(a.tenantId());
        assertThat(found.userId()).isEqualTo(a.reviewer().id());
        assertThat(found.email()).isEqualTo(a.reviewer().email());

        TenantContext.runWithoutTenant(() -> transaction.execute(status -> {
            jdbc.sql("SELECT set_config('lexvay.auth_lookup', 'on', true)").query().singleRow();
            assertThat(count()).as("reads, of every tenant, as the lookup of users does").isGreaterThanOrEqualTo(2);
            assertThat(jdbc.sql("UPDATE user_setup_token SET used_at = now()").update()).as("cannot update").isZero();
            assertThat(jdbc.sql("DELETE FROM user_setup_token").update()).as("cannot delete").isZero();
            assertThat(jdbc.sql("UPDATE app_user SET active = false").update()).as("cannot update users either").isZero();
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO user_setup_token (tenant_id, user_id, token_hash, expires_at) "
                    + "VALUES (:t, :u, :h, now() + interval '1 hour')")
                    .param("t", a.tenantId()).param("u", a.reviewer().id()).param("h", "d".repeat(64)).update())
                    .isInstanceOf(DataAccessException.class);
            return null;
        }));
        assertThat(TenantContext.runWithoutTenant(this::count)).as("the flag ended with its transaction").isZero();
    }

    @Test
    void aCodeThatIsUsedIsNoLongerFound() {
        TenantContext.runAs(a.tenantId(), () -> assertThat(repository.markUsed(tokenOfA)).isTrue());
        assertThat(TenantContext.runWithoutTenant(() -> repository.findOpen(hashOfA))).isEmpty();
    }
}
