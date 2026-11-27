package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.SchemaFixtures;
import com.lexvay.common.TenantAwareDataSource;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The tenant setting is no longer cleared when a connection is returned, because every checkout sets or
 * resets it. This proves that on one physical connection: a pool of size one lends the same backend to
 * tenant A, to nobody and to tenant B, and each caller sees exactly its own rows.
 */
class Rule7_PooledConnectionRebindsTest extends AbstractDatabaseTest {

    record Seen(int backendPid, long programs, String programTenant, String tenantSetting) {
    }

    @Autowired TestTenants tenants;
    @Autowired SchemaFixtures fixtures;

    @Test
    void aReturnedConnectionNeverCarriesItsPreviousTenantIntoTheNextCheckout() {
        var a = tenants.create("alpha");
        var b = tenants.create("beta");
        fixtures.insertChain(a.tenantId(), a.admin().id());
        fixtures.insertChain(b.tenantId(), b.admin().id());

        try (HikariDataSource pool = singleConnectionPool()) {
            JdbcClient jdbc = JdbcClient.create(new TenantAwareDataSource(pool));

            Seen asA = TenantContext.runAs(a.tenantId(), () -> look(jdbc));
            assertThat(asA.programs()).isEqualTo(1);
            assertThat(asA.programTenant()).isEqualTo(a.tenantId().toString());
            assertThat(asA.tenantSetting()).isEqualTo(a.tenantId().toString());

            Seen asNobody = TenantContext.runWithoutTenant(() -> look(jdbc));
            assertThat(asNobody.backendPid()).as("same physical connection").isEqualTo(asA.backendPid());
            assertThat(asNobody.programs()).as("no tenant sees no rows").isZero();
            assertThat(asNobody.tenantSetting()).isNullOrEmpty();

            Seen asB = TenantContext.runAs(b.tenantId(), () -> look(jdbc));
            assertThat(asB.backendPid()).isEqualTo(asA.backendPid());
            assertThat(asB.programs()).isEqualTo(1);
            assertThat(asB.programTenant()).as("tenant B sees its own program, not tenant A's").isEqualTo(b.tenantId().toString());
            assertThat(asB.tenantSetting()).isEqualTo(b.tenantId().toString());

            Seen asNobodyAgain = TenantContext.runWithoutTenant(() -> look(jdbc));
            assertThat(asNobodyAgain.backendPid()).isEqualTo(asA.backendPid());
            assertThat(asNobodyAgain.programs()).isZero();
        }
    }

    private static Seen look(JdbcClient jdbc) {
        return jdbc.sql("SELECT pg_backend_pid() AS pid, (SELECT count(*) FROM program) AS programs, "
                        + "(SELECT min(tenant_id::text) FROM program) AS program_tenant, current_setting('lexvay.tenant_id', true) AS tenant")
                .query((rs, i) -> new Seen(rs.getInt("pid"), rs.getLong("programs"), rs.getString("program_tenant"), rs.getString("tenant"))).single();
    }

    private static HikariDataSource singleConnectionPool() {
        HikariDataSource pool = new HikariDataSource();
        pool.setJdbcUrl(jdbcUrl());
        pool.setUsername(APP_USER);
        pool.setPassword(APP_PASSWORD);
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(1);
        pool.setPoolName("rule7-one-connection");
        return pool;
    }
}
