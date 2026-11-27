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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 5 for setup codes: a code is never edited or removed, only marked used. */
class Rule5_UserSetupTokenImmutabilityTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired SetupTokenRepository repository;
    @Autowired JdbcClient jdbc;

    TestTenants.TenantWithUsers tenant;
    UUID tokenId;

    @BeforeEach
    void setUp() {
        tenant = tenants.create("token-immutable");
        tokenId = UUID.randomUUID();
        TenantContext.runAs(tenant.tenantId(), () -> repository.insert(tokenId, tenant.tenantId(), tenant.reviewer().id(),
                UUID.randomUUID().toString().replace("-", "").repeat(2), OffsetDateTime.now().plusHours(72), tenant.admin().id()));
    }

    private int update(String sql) {
        return TenantContext.runAs(tenant.tenantId(), () -> jdbc.sql(sql).param("id", tokenId).update());
    }

    @ParameterizedTest(name = "UPDATE user_setup_token SET {0}")
    @ValueSource(strings = {"expires_at = expires_at + interval '1 day'", "token_hash = repeat('a', 64)", "user_id = created_by",
            "created_at = now() - interval '1 day'", "used_at = NULL, expires_at = now() + interval '9 days'"})
    void nothingButTheMomentOfUseCanChange(String assignment) {
        assertThatThrownBy(() -> update("UPDATE user_setup_token SET " + assignment + " WHERE id = :id"))
                .hasMessageContaining("only used_at may change");
    }

    @Test
    void theMomentOfUseCanBeSet() {
        assertThat(update("UPDATE user_setup_token SET used_at = now() WHERE id = :id")).isEqualTo(1);
    }

    @Test
    void aCodeCannotBeDeleted() {
        assertThatThrownBy(() -> update("DELETE FROM user_setup_token WHERE id = :id")).hasMessageContaining("immutable");
    }

    @Test
    void aCodeIsStoredAsTheHashOfItsCodeOrNotAtAll() {
        assertThatThrownBy(() -> TenantContext.runAs(tenant.tenantId(), () -> repository.insert(UUID.randomUUID(), tenant.tenantId(),
                tenant.reviewer().id(), "not-a-hash", OffsetDateTime.now().plusHours(1), null)))
                .hasMessageContaining("user_setup_token_token_hash_check");
    }
}
