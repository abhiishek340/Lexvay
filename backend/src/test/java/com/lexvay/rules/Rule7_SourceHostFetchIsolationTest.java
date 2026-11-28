package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.rule.source.SourceHostRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Product rule 7 for the one table that is not tenant data (RG-2, D-570): what each host has been asked and told is
 * shared by every tenant and instance, so it has no tenant_id, and no session can read or write it unless it holds the
 * transaction-local worker flag that only {@link SourceHostRepository} sets.
 */
class Rule7_SourceHostFetchIsolationTest extends AbstractDatabaseTest {

    private static final String HOST = "isolation.example.test";

    @Autowired TestTenants tenants;
    @Autowired SourceHostRepository hosts;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate transaction;

    UUID tenant;

    @BeforeEach
    void setUp() {
        tenant = tenants.create("host-rows").tenantId();
        hosts.saveRobots(HOST, 200, "User-agent: *\nDisallow: /private/\n");
    }

    private long visibleRows() {
        return jdbc.sql("SELECT count(*) FROM source_host_fetch WHERE host = :host").param("host", HOST).query(Long.class).single();
    }

    @Test
    void theTableIsNotTenantDataButIsProtected() {
        var row = jdbc.sql("SELECT c.relrowsecurity AS enabled, c.relforcerowsecurity AS forced, "
                + "(SELECT count(*) FROM pg_policies p WHERE p.tablename = c.relname) AS policies, "
                + "EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = c.relname AND column_name = 'tenant_id') AS has_tenant "
                + "FROM pg_class c WHERE c.relname = 'source_host_fetch'").query().singleRow();

        assertThat(row).containsEntry("enabled", true).containsEntry("forced", true).containsEntry("policies", 1L).containsEntry("has_tenant", false);
    }

    @Test
    void aTenantSeesNoHostRowsAndCannotWriteThem() {
        TenantContext.runAs(tenant, () -> {
            assertThat(visibleRows()).isZero();
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO source_host_fetch (host) VALUES ('tenant.example.test')").update())
                    .isInstanceOf(DataAccessException.class);
            assertThat(jdbc.sql("UPDATE source_host_fetch SET last_fetch_at = now() WHERE host = :host").param("host", HOST).update()).isZero();
            assertThat(jdbc.sql("DELETE FROM source_host_fetch WHERE host = :host").param("host", HOST).update()).isZero();
        });
    }

    @Test
    void noTenantFailsClosedToo() {
        TenantContext.runWithoutTenant(() -> {
            assertThat(visibleRows()).isZero();
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO source_host_fetch (host) VALUES ('nobody.example.test')").update())
                    .isInstanceOf(DataAccessException.class);
            return null;
        });
    }

    @Test
    void theWorkerFlagOpensItAndTheRepositoryIsWhatSetsIt() {
        long withTheFlag = transaction.execute(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            return visibleRows();
        });
        assertThat(withTheFlag).isEqualTo(1);
        assertThat(visibleRows()).as("the flag ended with the transaction").isZero();
        assertThat(hosts.freshRobots(HOST, Duration.ofHours(1))).isPresent();
        assertThat(hosts.freshRobots("unknown.example.test", Duration.ofHours(1))).isEmpty();
    }

    @Test
    void aHostNameIsStoredInLowerCaseOnly() {
        assertThatThrownBy(() -> hosts.saveRobots("Mixed.Example.Test", 200, "")).isInstanceOf(DataAccessException.class);
    }
}
