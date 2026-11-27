package com.lexvay.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.match.Passage;
import com.lexvay.assessment.match.PassageSearch;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Budget of ROADMAP F7, still met with F7c's six search terms per requirement: one BM25 statement for a requirement, headlines of the long pages among its top eight included, has a p95
 * of at most 30 ms on a program of 500 pages. The pages are synthetic: words drawn from a skewed vocabulary of 4,000 words, each
 * page with the policy sentences every syllabus repeats, 2,000 to 3,500 characters long so that part of the shortlist is excerpted.
 */
@Tag("perf")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PassageSearchLatencyTest extends AbstractDatabaseTest {

    static final int PAGES = 500;
    static final int WARM_UP = 50;
    static final int REQUESTS = 200;
    static final long BUDGET_MS = 30;

    static final String POLICY = "Students are expected to follow the professional ethics of counseling in this course. "
            + "Academic integrity, accommodations and the grading scale apply to every student in the program. ";

    @Autowired PassageSearch search;
    @Autowired TestTenants tenants;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired Flyway flyway;

    TestTenants.TenantWithUsers tenant;
    UUID programId;

    @BeforeAll
    void insertFiveHundredPages() throws SQLException {
        tenant = tenants.create("perf");
        programId = UUID.randomUUID();
        Random random = new Random(7);
        TenantContext.runAs(tenant.tenantId(), () -> transaction.execute(status -> {
            jdbc.sql("INSERT INTO program (id, tenant_id, name, license_type, catalog_year, created_by) VALUES (:id, :t, 'Perf program', 'FIXTURE_LICENSE', '2026-27', :u)")
                    .param("id", programId).param("t", tenant.tenantId()).param("u", tenant.admin().id()).update();
            for (int i = 0; i < PAGES; i++) {
                UUID documentId = UUID.randomUUID();
                String text = pageText(random);
                jdbc.sql("INSERT INTO document (id, tenant_id, program_id, filename, sha256, storage_key, kind, created_by) "
                                + "VALUES (:id, :t, :p, :name, :sha, :key, 'SYLLABUS', :u)")
                        .param("id", documentId).param("t", tenant.tenantId()).param("p", programId).param("name", "synthetic-" + i + ".pdf")
                        .param("sha", String.format("%064x", i)).param("key", tenant.tenantId() + "/" + documentId).param("u", tenant.admin().id()).update();
                jdbc.sql("INSERT INTO document_page (tenant_id, document_id, page_number, text, char_start, char_end, created_by) "
                                + "VALUES (:t, :d, 1, :text, 0, :end, :u)")
                        .param("t", tenant.tenantId()).param("d", documentId).param("text", text).param("end", text.length())
                        .param("u", tenant.admin().id()).update();
            }
            return null;
        }));
        analyzeAsOwner();
    }

    /** Autovacuum would have analyzed the tables of a program this size; a test cannot wait for it, and the application role may not ANALYZE. */
    private void analyzeAsOwner() throws SQLException {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE document");
            statement.execute("ANALYZE document_page");
        }
    }

    private static String pageText(Random random) {
        StringBuilder sb = new StringBuilder(POLICY);
        int target = 2_000 + random.nextInt(1_500);
        int sentence = 0;
        while (sb.length() < target) {
            sb.append("word").append((int) (Math.pow(random.nextDouble(), 3) * 4_000));
            sb.append(++sentence % 12 == 0 ? ".\n" : " ");
        }
        return sb.toString();
    }

    @Test
    void bm25ShortlistForARequirementOnFiveHundredPagesIsWithinBudget() {
        List<String[]> requirements = List.of(
                new String[] {"Ethics coursework", "coursework in professional ethics and legal standards for counselors word12 word45", "code of ethics,confidentiality,word77,word150,informed consent,liability"},
                new String[] {"Group counseling", "coursework in group counseling theory and practice word7 word300", "group therapy,group dynamics,word33,word2000,facilitation,process groups"},
                new String[] {"Supervised practice", "a course in which students practice counseling skills under supervision word2 word99", "practicum,role play,word41,word1500,skills lab,live observation"},
                new String[] {"Diagnosis", "coursework in the diagnosis and treatment planning of mental disorders word1000 word3", "psychopathology,abnormal psychology,word88,word2500,dsm,diagnostic criteria"},
                new String[] {"Human growth", "coursework in human growth and development across the lifespan word5 word64", "lifespan development,aging,word19,word3000,life stages,cognitive development"},
                new String[] {"Research", "coursework in research methods and program evaluation word8 word21", "statistics,outcome research,word55,word999,data analysis,needs assessment"});
        TenantContext.runAs(tenant.tenantId(), () -> {
            for (int i = 0; i < WARM_UP; i++) {
                run(requirements.get(i % requirements.size()));
            }
            long[] millis = new long[REQUESTS];
            for (int i = 0; i < REQUESTS; i++) {
                long start = System.nanoTime();
                List<Passage> shortlist = run(requirements.get(i % requirements.size()));
                millis[i] = (System.nanoTime() - start) / 1_000_000;
                assertThat(shortlist).hasSize(8);
            }
            Arrays.sort(millis);
            long p95 = millis[(int) Math.ceil(REQUESTS * 0.95) - 1];
            System.out.println("BM25 shortlist over " + PAGES + " pages, " + REQUESTS + " requests: p50=" + millis[REQUESTS / 2] + " ms, p95=" + p95
                    + " ms, max=" + millis[REQUESTS - 1] + " ms");
            assertThat(p95).as("p95 latency in ms").isLessThanOrEqualTo(BUDGET_MS);
            return null;
        });
    }

    private List<Passage> run(String[] requirement) {
        return new ArrayList<>(search.search(programId, requirement[0], requirement[1], List.of(requirement[2].split(",")), 8));
    }
}
