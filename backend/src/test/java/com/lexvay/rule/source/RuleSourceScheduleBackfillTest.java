package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * Flyway applies every migration before the application starts, so no test can load rows and then migrate. Like
 * {@code JobEntityIdBackfillTest}, this runs the backfill statement of V016 itself, read from the migration file,
 * against a temporary table named {@code rule_source} (which shadows the real one for this connection only).
 */
class RuleSourceScheduleBackfillTest extends AbstractDatabaseTest {

    private static final String MIGRATION = "db/migration/V016__rule_source_schedule.sql";

    @Autowired DataSource dataSource;

    /** The one UPDATE of the migration, from its first keyword to its semicolon. */
    private static String backfillStatement() throws Exception {
        String sql = new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int start = sql.indexOf("UPDATE rule_source");
        assertThat(start).as("backfill statement in " + MIGRATION).isNotNegative();
        return sql.substring(start, sql.indexOf(';', start));
    }

    @Test
    void aCheckedSourceIsDueOneIntervalAfterItsLastCheckAndANeverCheckedOneIsDueNow() throws Exception {
        Map<String, Long> minutesFromNow = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection(); Statement st = connection.createStatement()) {
            st.execute("CREATE TEMPORARY TABLE rule_source (name text PRIMARY KEY, last_checked_at timestamptz, "
                    + "check_interval_hours integer NOT NULL, next_check_at timestamptz)");
            try {
                st.execute("INSERT INTO rule_source (name, last_checked_at, check_interval_hours) VALUES "
                        + "('never checked', NULL, 24), "
                        + "('checked an hour ago', now() - interval '1 hour', 24), "
                        + "('overdue', now() - interval '30 hours', 24), "
                        + "('weekly', now() - interval '2 days', 168)");

                assertThat(st.executeUpdate(backfillStatement())).as("every source is visited").isEqualTo(4);

                try (ResultSet rs = st.executeQuery("SELECT name, CAST(round(extract(epoch FROM next_check_at - now()) / 60) AS bigint) "
                        + "FROM rule_source ORDER BY name")) {
                    while (rs.next()) {
                        minutesFromNow.put(rs.getString(1), rs.getLong(2));
                    }
                }
            } finally {
                // The connection goes back to the pool: leave no table that shadows the real one.
                st.execute("DROP TABLE pg_temp.rule_source");
            }
        }
        assertThat(minutesFromNow)
                .containsEntry("never checked", 0L)
                .containsEntry("checked an hour ago", 23L * 60)
                .containsEntry("overdue", -6L * 60)
                .containsEntry("weekly", 5L * 24 * 60);
    }
}
