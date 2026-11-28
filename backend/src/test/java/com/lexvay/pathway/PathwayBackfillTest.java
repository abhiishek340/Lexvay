package com.lexvay.pathway;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * Flyway applies every migration before the application starts, so no test can load rows and then migrate. This test
 * runs the two backfill statements of V024 itself, read from the migration file, against temporary tables that shadow
 * the real ones for this connection only (as RequirementOrdinalBackfillTest does for V012).
 */
class PathwayBackfillTest extends AbstractDatabaseTest {

    private static final String MIGRATION = "db/migration/V024__academic_pathway.sql";
    private static final String TENANT = "00000000-0000-0000-0000-0000000000a1";
    private static final String P1 = "00000000-0000-0000-0000-0000000000b1";
    private static final String P2 = "00000000-0000-0000-0000-0000000000b2";
    private static final String P3 = "00000000-0000-0000-0000-0000000000b3";

    @Autowired DataSource dataSource;

    /** The statement of the migration that starts with {@code start}, up to its semicolon. */
    private static String statement(String start) throws Exception {
        String sql = new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int from = sql.indexOf(start);
        assertThat(from).as(start + " in " + MIGRATION).isNotNegative();
        return sql.substring(from, sql.indexOf(';', from));
    }

    private static void course(Statement st, String program, String id) throws SQLException {
        st.execute("INSERT INTO course (id, tenant_id, program_id) VALUES ('" + id + "', '" + TENANT + "', '" + program + "')");
    }

    private static void version(Statement st, String course, String year, boolean required) throws SQLException {
        st.execute("INSERT INTO course_version (course_id, catalog_year, required) VALUES ('" + course + "', '" + year + "', " + required + ")");
    }

    private static String id(int n) {
        return "00000000-0000-0000-0000-00000000c%03d".formatted(n);
    }

    @Test
    void theBackfillGroupsTheRequiredCoursesAndTheElectivesOfEachProgramAndCatalogYearAndAssumesNoMinimum() throws Exception {
        List<String> groups = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); Statement st = connection.createStatement()) {
            st.execute("CREATE TEMPORARY TABLE course (id uuid PRIMARY KEY, tenant_id uuid NOT NULL, program_id uuid NOT NULL)");
            st.execute("CREATE TEMPORARY TABLE course_version (course_id uuid NOT NULL, catalog_year text NOT NULL, required boolean NOT NULL)");
            st.execute("CREATE TEMPORARY TABLE pathway_group (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, "
                    + "program_id uuid NOT NULL, catalog_year text NOT NULL, label text NOT NULL, kind text NOT NULL, min_count integer, min_credits numeric)");
            st.execute("CREATE TEMPORARY TABLE pathway_group_course (group_id uuid NOT NULL, course_id uuid NOT NULL, tenant_id uuid NOT NULL)");
            try {
                // Program 1: two required courses and one elective in its only catalog year.
                course(st, P1, id(1));
                course(st, P1, id(2));
                course(st, P1, id(3));
                version(st, id(1), "2026-27", true);
                version(st, id(2), "2026-27", true);
                version(st, id(3), "2026-27", false);
                // Program 2: one course whose second catalog year made it an elective; nothing else.
                course(st, P2, id(4));
                version(st, id(4), "2026-27", true);
                version(st, id(4), "2027-28", false);
                // Program 3 has only required courses, so it has no electives group; a program without courses has no group at all.
                course(st, P3, id(5));
                version(st, id(5), "2026-27", true);

                assertThat(st.executeUpdate(statement("INSERT INTO pathway_group (tenant_id"))).isEqualTo(5);
                assertThat(st.executeUpdate(statement("INSERT INTO pathway_group_course"))).as("one row per course in each group").isEqualTo(6);

                try (ResultSet rs = st.executeQuery("SELECT g.program_id || ' ' || g.catalog_year || ' ' || g.label || ' ' || g.kind || ' min=' "
                        + "|| coalesce(g.min_count::text, 'null') || '/' || coalesce(g.min_credits::text, 'null') || ' ' "
                        + "|| coalesce((SELECT string_agg(gc.course_id::text, ',' ORDER BY gc.course_id) FROM pathway_group_course gc WHERE gc.group_id = g.id), '') "
                        + "FROM pathway_group g ORDER BY g.program_id, g.catalog_year, g.kind")) {
                    while (rs.next()) {
                        groups.add(rs.getString(1));
                    }
                }
            } finally {
                // The connection goes back to the pool: leave no table that shadows a real one.
                for (String table : List.of("pathway_group_course", "pathway_group", "course_version", "course")) {
                    st.execute("DROP TABLE pg_temp." + table);
                }
            }
        }
        assertThat(groups).containsExactly(
                P1 + " 2026-27 Required courses ALL_OF min=null/null " + id(1) + "," + id(2),
                P1 + " 2026-27 Electives CHOOSE_COUNT min=null/null " + id(3),
                P2 + " 2026-27 Required courses ALL_OF min=null/null " + id(4),
                P2 + " 2027-28 Electives CHOOSE_COUNT min=null/null " + id(4),
                P3 + " 2026-27 Required courses ALL_OF min=null/null " + id(5));
    }
}
