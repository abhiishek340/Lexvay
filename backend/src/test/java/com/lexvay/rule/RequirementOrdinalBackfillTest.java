package com.lexvay.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * Flyway applies every migration before the application starts, so no test can load rows and then migrate.
 * This test runs the backfill statement of V012 itself, read from the migration file, against a temporary
 * table named {@code requirement} (which shadows the real one for this connection only).
 */
class RequirementOrdinalBackfillTest extends AbstractDatabaseTest {

    private static final String MIGRATION = "db/migration/V012__requirement_ordinal.sql";
    private static final String V1 = "00000000-0000-0000-0000-000000000001";
    private static final String V2 = "00000000-0000-0000-0000-000000000002";

    @Autowired DataSource dataSource;

    /** The one UPDATE of the migration, from its first keyword to its semicolon. */
    private static String backfillStatement() throws Exception {
        String sql = new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int start = sql.indexOf("UPDATE requirement");
        assertThat(start).as("backfill statement in " + MIGRATION).isNotNegative();
        return sql.substring(start, sql.indexOf(';', start));
    }

    private static void insert(Statement st, String version, String ref, String createdAt) throws SQLException {
        st.execute("INSERT INTO requirement (id, rule_version_id, ref, created_at) VALUES (gen_random_uuid(), '" + version + "', '"
                + ref + "', '" + createdAt + "')");
    }

    @Test
    void backfillOrdersByCreationTimeThenTrailingNumberThenRefWithinEachRuleVersion() throws Exception {
        Map<String, Integer> ordinals = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection(); Statement st = connection.createStatement()) {
            st.execute("CREATE TEMPORARY TABLE requirement (id uuid PRIMARY KEY, rule_version_id uuid NOT NULL, ref text NOT NULL, "
                    + "created_at timestamptz NOT NULL, ordinal integer)");
            try {
                // Version 1: one transaction's rows share a timestamp. Inserted shuffled; text order would put CO-10 before CO-2.
                for (String ref : List.of("CO-10", "CO-2", "ZZ-NOTE", "CO-1", "CO-12", "AA-NOTE", "CO-9", "CO-11", "CO-3")) {
                    insert(st, V1, ref, "2026-03-01 10:00:00+00");
                }
                // Version 2: an earlier row sorts first whatever its ref; numbering restarts at 1 for each version.
                insert(st, V2, "X-1", "2026-03-02 10:00:01+00");
                insert(st, V2, "X-5", "2026-03-02 10:00:00+00");

                assertThat(st.executeUpdate(backfillStatement())).isEqualTo(11);

                try (ResultSet rs = st.executeQuery("SELECT rule_version_id || ' ' || ref, ordinal FROM requirement ORDER BY rule_version_id, ordinal")) {
                    while (rs.next()) {
                        ordinals.put(rs.getString(1), rs.getInt(2));
                    }
                }
            } finally {
                // The connection goes back to the pool: leave no table that shadows the real one.
                st.execute("DROP TABLE pg_temp.requirement");
            }
        }
        assertThat(ordinals).containsExactly(
                Map.entry(V1 + " CO-1", 1), Map.entry(V1 + " CO-2", 2), Map.entry(V1 + " CO-3", 3), Map.entry(V1 + " CO-9", 4),
                Map.entry(V1 + " CO-10", 5), Map.entry(V1 + " CO-11", 6), Map.entry(V1 + " CO-12", 7),
                Map.entry(V1 + " AA-NOTE", 8), Map.entry(V1 + " ZZ-NOTE", 9),
                Map.entry(V2 + " X-5", 1), Map.entry(V2 + " X-1", 2));
    }
}
