package com.lexvay.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.auth.Role;
import com.lexvay.llm.LlmClient;
import com.lexvay.seed.ScriptedFakeModel;
import com.lexvay.seed.SeedLoader;
import com.lexvay.tenant.AppUserRepository;
import com.lexvay.workqueue.WorkQueueRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves with {@code EXPLAIN}, as the application role under row-level security, that the planner chooses
 * each index added by V008 for the query it was added for, and ignores the candidates that were left out. The database holds 100 renamed copies of the seed
 * (about 100 tenants), so tables are large enough for the planner to prefer an index over a sequential scan.
 * Run through {@code make perf}.
 */
@Tag("perf")
class IndexUsageTest extends AbstractDatabaseTest {

    static final int COPIES = Integer.getInteger("lexvay.perf.indexCopies", 100);
    static final String PASSWORD = "perf-demo-password";
    static final Path SEED_DIR = Path.of("..", "seed");

    /** An index and the application query it was considered for; a rejected one is created here only to prove it is ignored. */
    record Candidate(String index, String query, String sql, boolean kept) {
        Candidate(String index, String query, String sql) {
            this(index, query, sql, true);
        }
    }

    /** Candidates from the work item that the planner ignores. They are not in V008; this test creates them to show why. */
    static final List<String> REJECTED_DDL = List.of(
            "CREATE INDEX rejected_document_program_idx ON document (program_id)",
            "CREATE INDEX rejected_change_event_status_created_idx ON change_event (status, created_at DESC)",
            "CREATE INDEX rejected_job_document_id_idx ON job ((payload->>'documentId'))",
            "CREATE INDEX rejected_job_rule_version_id_idx ON job ((payload->>'ruleVersionId'))");

    @Autowired SeedLoader loader;
    @Autowired LlmClient llm;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired JdbcClient jdbc;

    @Test
    void thePlannerChoosesEveryKeptIndexAndIgnoresTheRejectedOnes() throws Exception {
        ScriptedFakeModel model = ScriptedFakeModel.load(SEED_DIR, llm, json);
        loader.load(SEED_DIR, PASSWORD, model);
        PerfSeed.loadCopies(loader, SEED_DIR, PASSWORD, model, COPIES);
        try (Connection admin = DriverManager.getConnection(jdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            PerfSeed.addHistoryVolume(statement);
            for (String ddl : REJECTED_DDL) {
                statement.execute(ddl);
            }
            statement.execute("ANALYZE");
        }
        UUID tenantId = users.findForLogin("dana.whitfield@northfield.example").orElseThrow().tenantId();

        List<String> report = new ArrayList<>();
        report.add("| Index | Query | Kept in V008 | Planner choice (application role, row-level security) | Chosen | Planner choice without row-level security |");
        report.add("|---|---|---|---|---|---|");
        List<String> keptButIgnored = new ArrayList<>();
        List<String> rejectedButChosen = new ArrayList<>();
        try (Connection admin = DriverManager.getConnection(jdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (Candidate candidate : TenantContext.runAs(tenantId, this::candidates)) {
                List<String> plan = TenantContext.runAs(tenantId,
                        () -> jdbc.sql("EXPLAIN " + candidate.sql()).query(String.class).list());
                boolean chosen = plan.stream().anyMatch(line -> line.contains(candidate.index()));
                report.add("| `" + candidate.index() + "` | " + candidate.query() + " | " + (candidate.kept() ? "yes" : "no") + " | " + scans(plan) + " | " + (chosen ? "yes" : "no")
                        + " | " + scans(explain(admin, candidate.sql())) + " |");
                if (candidate.kept() && !chosen) {
                    keptButIgnored.add(candidate.index());
                }
                if (!candidate.kept() && chosen) {
                    rejectedButChosen.add(candidate.index());
                }
            }
        }
        List<String> workQueue = new ArrayList<>();
        for (Role role : Role.values()) {
            workQueue.add("-- role " + role);
            workQueue.addAll(TenantContext.runAs(tenantId, () -> jdbc.sql(
                    "EXPLAIN (ANALYZE, BUFFERS) " + WorkQueueRepository.LIST.replace(":role", "'" + role + "'")).query(String.class).list()));
        }
        Path plan = Path.of("target", "perf", "work-queue-plan.txt");
        Files.createDirectories(plan.getParent());
        Files.writeString(plan, String.join("\n", workQueue) + "\n");

        String table = String.join("\n", report) + "\n";
        System.out.println(table);
        Path out = Path.of("target", "perf", "index-usage.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, table);
        assertThat(keptButIgnored).as("indexes of V008 the planner did not choose: drop them").isEmpty();
        assertThat(rejectedButChosen).as("rejected candidates the planner now chooses: add them to V008").isEmpty();
        assertThat(workQueue).as("the work queue statement scans no hot table sequentially (plan in target/perf/work-queue-plan.txt)")
                .noneMatch(line -> line.matches(".*Seq Scan on (mapping|review_event|job)\\b.*") && !line.contains("never executed"));
    }

    /** The access paths of a plan, without costs and conditions. */
    private static String scans(List<String> plan) {
        return plan.stream().map(String::strip)
                .filter(l -> l.matches("^(->\\s*)?(Seq Scan|Index Scan|Index Only Scan|Bitmap Heap Scan|Bitmap Index Scan).*"))
                .map(l -> l.replaceAll("\\s*\\(cost=.*", "").replaceFirst("^->\\s*", "")).collect(Collectors.joining("; "));
    }

    private static List<String> explain(Connection connection, String sql) throws SQLException {
        List<String> plan = new ArrayList<>();
        try (Statement statement = connection.createStatement(); var rows = statement.executeQuery("EXPLAIN " + sql)) {
            while (rows.next()) {
                plan.add(rows.getString(1));
            }
        }
        return plan;
    }

    /** The statements of the application's hot paths, with real ids of the first seeded tenant. */
    private List<Candidate> candidates() {
        String program = one("SELECT id FROM program ORDER BY created_at LIMIT 1");
        String assessment = one("SELECT id FROM assessment WHERE program_id = '" + program + "' AND status = 'FINISHED' LIMIT 1");
        String jurisdiction = one("SELECT id FROM jurisdiction WHERE code = 'CA'");
        String courseVersion = one("SELECT course_version_id FROM document WHERE course_version_id IS NOT NULL LIMIT 1");
        String document = one("SELECT id FROM document WHERE course_version_id = '" + courseVersion + "'");
        String ruleVersion = one("SELECT id FROM rule_version LIMIT 1");
        return List.of(
                new Candidate("mapping_assessment_jurisdiction_idx", "mappings of one jurisdiction in an assessment (list, counts)",
                        "SELECT m.id FROM mapping m JOIN requirement r ON r.id = m.requirement_id "
                                + "WHERE m.assessment_id = '" + assessment + "' AND m.jurisdiction_id = '" + jurisdiction + "'"),
                new Candidate("mapping_requirement_idx", "mark mappings stale when a rule version is superseded",
                        "UPDATE mapping SET stale = true WHERE requirement_id IN "
                                + "(SELECT id FROM requirement WHERE rule_version_id = '" + ruleVersion + "')"),
                new Candidate("mapping_evidence_span_idx", "mark mappings stale when a course version is superseded",
                        "UPDATE mapping SET stale = true WHERE evidence_span_id IN (SELECT es.id FROM evidence_span es "
                                + "JOIN document d ON d.id = es.document_id WHERE d.course_version_id = '" + courseVersion + "')"),
                new Candidate("determination_program_jurisdiction_status_idx", "is there a signed determination for the jurisdiction",
                        "SELECT count(*) FROM determination WHERE program_id = '" + program + "' AND jurisdiction_id = '"
                                + jurisdiction + "' AND status = 'SIGNED'"),
                new Candidate("determination_rule_version_idx", "reopen determinations of a superseded rule version",
                        "UPDATE determination SET status = 'REOPENED' WHERE rule_version_id = '" + ruleVersion
                                + "' AND status = 'SIGNED' RETURNING id"),
                new Candidate("rejected_document_program_idx", "documents of a program (unique index tenant_id, program_id, sha256 serves it)",
                        "SELECT id FROM document WHERE program_id = '" + program + "' ORDER BY created_at, id", false),
                new Candidate("document_course_version_idx", "documents of a course version",
                        "SELECT id FROM document WHERE course_version_id = '" + courseVersion + "' ORDER BY created_at, id"),
                new Candidate("evidence_span_document_idx", "spans cited from the documents of a course version",
                        "SELECT es.id FROM evidence_span es JOIN document d ON d.id = es.document_id "
                                + "WHERE d.course_version_id = '" + courseVersion + "'"),
                new Candidate("audit_log_entity_idx", "audit history of one record",
                        "SELECT id FROM audit_log WHERE entity_id = '" + program + "' ORDER BY created_at DESC"),
                new Candidate("assessment_program_status_finished_idx", "latest finished assessment of a program",
                        "SELECT id FROM assessment WHERE program_id = '" + program
                                + "' AND status = 'FINISHED' ORDER BY finished_at DESC, created_at DESC LIMIT 1"),
                new Candidate("rejected_change_event_status_created_idx", "open change events, newest first",
                        "SELECT id FROM change_event WHERE status = 'OPEN' ORDER BY created_at DESC", false),
                new Candidate("rejected_job_document_id_idx", "ingest status of a document",
                        "SELECT id FROM job WHERE type = 'INGEST_DOCUMENT' AND payload->>'documentId' = '" + document
                                + "' ORDER BY created_at DESC LIMIT 1", false),
                new Candidate("rejected_job_rule_version_id_idx", "extraction status of a rule version",
                        "SELECT id FROM job WHERE type = 'EXTRACT_REQUIREMENTS' AND payload->>'ruleVersionId' = '" + ruleVersion
                                + "' ORDER BY created_at DESC LIMIT 1", false),
                new Candidate("requirement_rule_version_id_ref_key", "requirements of a rule version (existing unique index, so V008 adds none)",
                        "SELECT id FROM requirement WHERE rule_version_id = '" + ruleVersion + "' ORDER BY created_at, ref"));
    }

    private String one(String sql) {
        return jdbc.sql(sql).query(String.class).single();
    }
}
