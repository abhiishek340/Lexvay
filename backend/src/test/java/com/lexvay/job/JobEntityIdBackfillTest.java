package com.lexvay.job;

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
 * Flyway applies every migration before the application starts, so no test can load rows and then migrate.
 * This test runs the backfill statement of V013 itself, read from the migration file, against a temporary
 * table named {@code job} (which shadows the real one for this connection only).
 */
class JobEntityIdBackfillTest extends AbstractDatabaseTest {

    private static final String MIGRATION = "db/migration/V013__job_entity_id.sql";
    private static final String DOCUMENT = "00000000-0000-0000-0000-00000000d0c1";
    private static final String RULE_VERSION = "00000000-0000-0000-0000-0000000000a1";
    private static final String RULE_SOURCE = "00000000-0000-0000-0000-0000000000b1";

    @Autowired DataSource dataSource;

    /** The one UPDATE of the migration, from its first keyword to its semicolon. */
    private static String backfillStatement() throws Exception {
        String sql = new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int start = sql.indexOf("UPDATE job");
        assertThat(start).as("backfill statement in " + MIGRATION).isNotNegative();
        return sql.substring(start, sql.indexOf(';', start));
    }

    @Test
    void backfillTakesTheSingleValueOfTheObjectPayloadAndLeavesOtherShapesAlone() throws Exception {
        Map<String, String> entityIds = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection(); Statement st = connection.createStatement()) {
            st.execute("CREATE TEMPORARY TABLE job (name text PRIMARY KEY, payload jsonb NOT NULL, entity_id uuid)");
            try {
                st.execute("INSERT INTO job (name, payload) VALUES "
                        + "('ingest', '{\"documentId\": \"" + DOCUMENT + "\"}'), "
                        + "('extraction', '{\"ruleVersionId\": \"" + RULE_VERSION + "\"}'), "
                        + "('rule source', jsonb_build_object('ruleSourceId', '" + RULE_SOURCE + "'::uuid)), "
                        + "('empty object', '{}'), "
                        + "('array', '[\"" + DOCUMENT + "\"]'), "
                        + "('string', to_jsonb('" + DOCUMENT + "'::text))");

                assertThat(st.executeUpdate(backfillStatement())).as("only objects are visited").isEqualTo(4);

                try (ResultSet rs = st.executeQuery("SELECT name, entity_id::text FROM job ORDER BY name")) {
                    while (rs.next()) {
                        entityIds.put(rs.getString(1), rs.getString(2));
                    }
                }
            } finally {
                // The connection goes back to the pool: leave no table that shadows the real one.
                st.execute("DROP TABLE pg_temp.job");
            }
        }
        assertThat(entityIds)
                .containsEntry("ingest", DOCUMENT)
                .containsEntry("extraction", RULE_VERSION)
                .containsEntry("rule source", RULE_SOURCE)
                .containsEntry("empty object", null)
                .containsEntry("array", null)
                .containsEntry("string", null);
    }
}
