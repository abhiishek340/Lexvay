package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Every table except jurisdiction has tenant_id, RLS enabled and forced, and a policy. */
class Rule7_EveryTableHasPolicyTest extends AbstractDatabaseTest {

    @Autowired JdbcClient jdbc;

    @Test
    void everyTenantTableIsProtected() {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT c.relname AS table_name, c.relrowsecurity AS enabled, c.relforcerowsecurity AS forced,
                       (SELECT count(*) FROM pg_policies p WHERE p.schemaname = 'public' AND p.tablename = c.relname) AS policies,
                       EXISTS (SELECT 1 FROM information_schema.columns col
                               WHERE col.table_schema = 'public' AND col.table_name = c.relname AND col.column_name = 'tenant_id') AS has_tenant_id
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r'
                ORDER BY c.relname
                """).query().listOfRows();

        assertThat(rows).hasSizeGreaterThan(15);
        for (var row : rows) {
            String table = (String) row.get("table_name");
            if (table.equals("jurisdiction") || table.equals("flyway_schema_history")) {
                assertThat(row.get("enabled")).as(table + " has no RLS by design").isEqualTo(false);
                continue;
            }
            assertThat(row.get("has_tenant_id")).as(table + " has tenant_id").isEqualTo(true);
            assertThat(row.get("enabled")).as(table + " RLS enabled").isEqualTo(true);
            assertThat(row.get("forced")).as(table + " RLS forced").isEqualTo(true);
            assertThat(((Number) row.get("policies")).intValue()).as(table + " policies").isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void applicationRoleCannotBypassRowLevelSecurity() {
        Boolean bypass = jdbc.sql("SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user").query(Boolean.class).single();
        assertThat(bypass).isFalse();
    }
}
