package com.lexvay.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * Flyway applies every migration before the application starts, so no test can load entries and then migrate. This
 * test runs the backfill statement of V019 itself, read from the migration file, against temporary tables named
 * {@code audit_log} and {@code audit_unsealed} (which shadow the real ones for this connection only).
 */
class AuditSealBackfillTest extends AbstractDatabaseTest {

    private static final String MIGRATION = "db/migration/V019__audit_seal.sql";

    @Autowired DataSource dataSource;

    /** The one backfilling INSERT of the migration, from its first keyword to its semicolon. */
    private static String backfillStatement() throws Exception {
        String sql = new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int start = sql.indexOf("INSERT INTO audit_unsealed");
        assertThat(start).as("backfill statement in " + MIGRATION).isNotNegative();
        return sql.substring(start, sql.indexOf(';', start));
    }

    @Test
    void everyExistingEntryOfEveryTenantIsQueuedWithItsOwnTimeAndTenant() throws Exception {
        List<String> queued = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); Statement st = connection.createStatement()) {
            st.execute("CREATE TEMPORARY TABLE audit_log (id uuid PRIMARY KEY, tenant_id uuid NOT NULL, created_at timestamptz NOT NULL)");
            st.execute("CREATE TEMPORARY TABLE audit_unsealed (audit_id uuid PRIMARY KEY, tenant_id uuid NOT NULL, created_at timestamptz NOT NULL)");
            try {
                st.execute("INSERT INTO audit_log VALUES "
                        + "('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000a1', '2026-01-01 10:00:00.123456+00'), "
                        + "('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-0000000000a1', '2026-01-02 10:00:00+00'), "
                        + "('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-0000000000b2', '2026-01-03 10:00:00+00')");

                assertThat(st.executeUpdate(backfillStatement())).isEqualTo(3);

                try (ResultSet rs = st.executeQuery("SELECT audit_id || ' ' || tenant_id || ' ' || (created_at AT TIME ZONE 'UTC') FROM audit_unsealed ORDER BY audit_id")) {
                    while (rs.next()) {
                        queued.add(rs.getString(1));
                    }
                }
            } finally {
                st.execute("DROP TABLE pg_temp.audit_unsealed");
                st.execute("DROP TABLE pg_temp.audit_log");
            }
        }

        assertThat(queued).containsExactly(
                "00000000-0000-0000-0000-000000000001 00000000-0000-0000-0000-0000000000a1 2026-01-01 10:00:00.123456",
                "00000000-0000-0000-0000-000000000002 00000000-0000-0000-0000-0000000000a1 2026-01-02 10:00:00",
                "00000000-0000-0000-0000-000000000003 00000000-0000-0000-0000-0000000000b2 2026-01-03 10:00:00");
    }
}
