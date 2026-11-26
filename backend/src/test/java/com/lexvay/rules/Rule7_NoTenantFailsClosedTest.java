package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.SchemaFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** With no tenant set, every tenant table is empty and rejects inserts: the policy fails closed. */
class Rule7_NoTenantFailsClosedTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired SchemaFixtures fixtures;
    @Autowired JdbcClient jdbc;

    @Test
    void noTenantSeesNothingAndWritesNothing() {
        var t = tenants.create("gamma");
        fixtures.insertChain(t.tenantId(), t.admin().id());

        List<String> tables = TenantContext.runWithoutTenant(() -> jdbc.sql(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' "
                                + "AND table_type = 'BASE TABLE' AND table_name NOT IN ('jurisdiction', 'flyway_schema_history')")
                .query(String.class).list());
        assertThat(tables).hasSizeGreaterThan(15);

        TenantContext.runWithoutTenant(() -> {
            for (String table : tables) {
                Long count = jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
                assertThat(count).as(table).isZero();
            }
            assertThatThrownBy(() -> jdbc.sql(
                            "INSERT INTO program (id, tenant_id, name, license_type, catalog_year) VALUES (:id, :t, 'x', 'y', 'z')")
                    .param("id", UUID.randomUUID()).param("t", t.tenantId()).update())
                    .isInstanceOf(DataAccessException.class)
                    .rootCause().hasMessageContaining("row-level security");
            return null;
        });
    }
}
