package com.lexvay.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.assessment.MappingRepository;
import com.lexvay.audit.AuditFilter;
import com.lexvay.audit.AuditRepository;
import com.lexvay.auth.Role;
import com.lexvay.coverage.CoverageRepository;
import com.lexvay.llm.LlmClient;
import com.lexvay.report.ReportRepository;
import com.lexvay.rule.source.RuleSourceRepository;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves with {@code EXPLAIN}, as the application role under row-level security, that the planner chooses
 * each index added by V008, V013, V016 and V018 for the query it was added for, and ignores the candidates that were left out. The database holds 100 renamed copies of the seed
 * (about 100 tenants), so tables are large enough for the planner to prefer an index over a sequential scan.
 * Run through {@code make perf}.
 */
@Tag("perf")
class IndexUsageTest extends AbstractDatabaseTest {

    static final int COPIES = Integer.getInteger("lexvay.perf.indexCopies", 100);
    static final String PASSWORD = "perf-demo-password";
    static final Path SEED_DIR = Path.of("..", "seed");
    /** A lookup by one record reads a handful of pages; the sequential scan it replaced read the whole table (over a thousand). */
    static final int JOB_LOOKUP_BUFFER_BUDGET = 100;
    /**
     * The mappings of an assessment of 500 rows read about 3 500 buffers (about 3 ms); before D-364 the same statement read
     * 4 700 to 5 200 and took 15 to 30 ms, and the work queue 280 ms, because the current-mapping filter was quadratic.
     */
    static final int MAPPINGS_BUFFER_BUDGET = 4000;
    static final Predicate<String> JOB_SEQUENTIAL_SCAN = line -> line.matches(".*Seq Scan on job\\b.*");
    /** Audit entries added to every tenant, and to the tenant the statements run as: about 60 000 and 20 000 rows in all. */
    static final int AUDIT_ROWS_PER_TENANT = 300;
    static final int AUDIT_ROWS_OF_MEASURED_TENANT = 20_000;
    /** A plain Sort holds every matching row before the first leaves; an Incremental Sort only the rows of one timestamp. */
    static final Predicate<String> FULL_SORT = line -> line.matches("^\\s*(->\\s*)?Sort\\s+\\(cost=.*");
    static final Predicate<String> AUDIT_SEQUENTIAL_SCAN = line -> line.matches(".*Seq Scan on audit_log\\b.*");
    static final String REPORT_CHECK_INDEX = "report_content_sha_idx";
    /** A check reads the one index entry of the hash and the rows it joins: a few dozen buffers, never the report table. */
    static final int REPORT_CHECK_BUFFER_BUDGET = 60;

    /** An index and the application query it was considered for; a rejected one is created here only to prove it is ignored. */
    record Candidate(String index, String query, String sql, boolean kept, String alternative) {
        Candidate(String index, String query, String sql) {
            this(index, query, sql, true, null);
        }

        Candidate(String index, String query, String sql, boolean kept) {
            this(index, query, sql, kept, null);
        }

        /** The planner may choose either index for the statement; both avoid reading the whole table (D-365). */
        static Candidate eitherOf(String index, String alternative, String query, String sql) {
            return new Candidate(index, query, sql, true, alternative);
        }

        boolean chosenIn(List<String> plan) {
            return plan.stream().anyMatch(line -> line.contains(index) || alternative != null && line.contains(alternative));
        }
    }

    /** Candidates from the work item that the planner ignores. They are not in V008; this test creates them to show why. */
    static final List<String> REJECTED_DDL = List.of(
            "CREATE INDEX rejected_document_program_idx ON document (program_id)",
            "CREATE INDEX rejected_change_event_status_created_idx ON change_event (status, created_at DESC)");

    @Autowired SeedLoader loader;
    @Autowired LlmClient llm;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate transaction;

    @Test
    void thePlannerChoosesEveryKeptIndexAndIgnoresTheRejectedOnes() throws Exception {
        ScriptedFakeModel model = ScriptedFakeModel.load(SEED_DIR, llm, json);
        loader.load(SEED_DIR, PASSWORD, model);
        PerfSeed.loadCopies(loader, SEED_DIR, PASSWORD, model, COPIES);
        UUID tenantId = users.findForLogin("dana.whitfield@northfield.example").orElseThrow().tenantId();
        PerfSeed.LargeAssessments large;
        try (Connection admin = DriverManager.getConnection(jdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            PerfSeed.addHistoryVolume(statement);
            large = PerfSeed.addLargeAssessments(statement, tenantId);
            PerfSeed.addAuditVolume(statement, tenantId, AUDIT_ROWS_PER_TENANT, AUDIT_ROWS_OF_MEASURED_TENANT);
            for (String ddl : REJECTED_DDL) {
                statement.execute(ddl);
            }
            statement.execute("ANALYZE");
        }

        List<String> report = new ArrayList<>();
        report.add("| Index | Query | Kept in V008 | Planner choice (application role, row-level security) | Chosen | Planner choice without row-level security |");
        report.add("|---|---|---|---|---|---|");
        List<String> keptButIgnored = new ArrayList<>();
        List<String> rejectedButChosen = new ArrayList<>();
        List<String> jobSequentialScans = new ArrayList<>();
        List<String> lookupBuffers = new ArrayList<>();
        List<String> lookupsOverBudget = new ArrayList<>();
        try (Connection admin = DriverManager.getConnection(jdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (Candidate candidate : TenantContext.runAs(tenantId, this::candidates)) {
                List<String> plan = TenantContext.runAs(tenantId,
                        () -> jdbc.sql("EXPLAIN " + candidate.sql()).query(String.class).list());
                boolean chosen = candidate.chosenIn(plan);
                report.add("| `" + candidate.index() + "` | " + candidate.query() + " | " + (candidate.kept() ? "yes" : "no") + " | " + scans(plan) + " | " + (chosen ? "yes" : "no")
                        + " | " + scans(explain(admin, candidate.sql())) + " |");
                if (plan.stream().anyMatch(JOB_SEQUENTIAL_SCAN)) {
                    jobSequentialScans.add(candidate.query());
                }
                if (candidate.index().equals("job_entity_idx")) {
                    List<String> analyzed = TenantContext.runAs(tenantId,
                            () -> jdbc.sql("EXPLAIN (ANALYZE, BUFFERS) " + candidate.sql()).query(String.class).list());
                    int touched = buffers(analyzed);
                    lookupBuffers.add("| " + candidate.query() + " | " + touched + " |");
                    if (touched > JOB_LOOKUP_BUFFER_BUDGET) {
                        lookupsOverBudget.add(candidate.query() + ": " + touched);
                    }
                }
                if (candidate.kept() && !chosen) {
                    keptButIgnored.add(candidate.index());
                }
                if (!candidate.kept() && chosen) {
                    rejectedButChosen.add(candidate.index());
                }
            }
        }
        List<String> scheduler = workerPlan("EXPLAIN " + RuleSourceRepository.ENQUEUE.formatted(RuleSourceRepository.DUE)
                .replace(":createdBy", "NULL"));
        boolean schedulerUsesIndex = scheduler.stream().anyMatch(line -> line.contains("job_claim_idx"));
        report.add("| `job_claim_idx` | is a check queued for every due source (scheduler, worker flag, all tenants; V001 index) | yes | "
                + scans(scheduler) + " | " + (schedulerUsesIndex ? "yes" : "no") + " | |");
        if (!schedulerUsesIndex) {
            keptButIgnored.add("job_claim_idx (scheduler)");
        }
        boolean dueUsesIndex = scheduler.stream().anyMatch(line -> line.contains("rule_source_due_idx"));
        report.add("| `rule_source_due_idx` | which active rule sources are due (scheduler, worker flag, all tenants; V016) | yes | "
                + scans(scheduler) + " | " + (dueUsesIndex ? "yes" : "no") + " | |");
        if (!dueUsesIndex) {
            keptButIgnored.add("rule_source_due_idx (scheduler)");
        }
        if (scheduler.stream().anyMatch(JOB_SEQUENTIAL_SCAN)) {
            jobSequentialScans.add("scheduler");
        }
        List<String> workQueue = new ArrayList<>();
        for (Role role : Role.values()) {
            workQueue.add("-- role " + role);
            workQueue.addAll(TenantContext.runAs(tenantId, () -> jdbc.sql(
                    "EXPLAIN (ANALYZE, BUFFERS) " + WorkQueueRepository.LIST.replace(":role", "'" + role + "'")).query(String.class).list()));
        }
        UUID northfieldProgram = TenantContext.runAs(tenantId, () -> jdbc.sql("SELECT id FROM program ORDER BY created_at LIMIT 1").query(UUID.class).single());
        List<String> coverage = TenantContext.runAs(tenantId, () -> jdbc.sql("EXPLAIN (ANALYZE, BUFFERS) " + CoverageRepository.MATRIX
                .replace(":program", "'" + northfieldProgram + "'").replace(":jurisdiction", "'CA'")).query(String.class).list());
        List<String> mappingPlans = new ArrayList<>();
        List<String> mappingsOverBudget = new ArrayList<>();
        for (var assessment : Map.of("before re-run (500 mappings, successor rule version without mappings)", large.beforeRerun(),
                "after re-run (500 hidden mappings of the superseded version, 500 current)", large.afterRerun()).entrySet()) {
            String explain = "EXPLAIN (ANALYZE, BUFFERS) " + MappingRepository.BY_ASSESSMENT.replace(":a", "'" + assessment.getValue() + "'");
            List<String> analyzed = null;
            for (int run = 0; run < 3; run++) {
                analyzed = TenantContext.runAs(tenantId, () -> jdbc.sql(explain).query(String.class).list());
            }
            if (buffers(analyzed) > MAPPINGS_BUFFER_BUDGET) {
                mappingsOverBudget.add(assessment.getKey() + ": " + buffers(analyzed));
            }
            mappingPlans.add("-- " + assessment.getKey());
            mappingPlans.addAll(analyzed);
        }
        Path mappingPlan = Path.of("target", "perf", "mappings-plan.txt");
        Files.createDirectories(mappingPlan.getParent());
        Files.writeString(mappingPlan, String.join("\n", mappingPlans) + "\n");

        List<String> auditPlans = new ArrayList<>();
        List<String> auditSorts = new ArrayList<>();
        for (var statement : auditStatements().entrySet()) {
            List<String> analyzed = null;
            for (int run = 0; run < 3; run++) {
                analyzed = TenantContext.runAs(tenantId,
                        () -> jdbc.sql("EXPLAIN (ANALYZE, BUFFERS) " + statement.getValue()).query(String.class).list());
            }
            auditPlans.add("-- " + statement.getKey());
            auditPlans.addAll(analyzed);
            if (analyzed.stream().anyMatch(AUDIT_SEQUENTIAL_SCAN)
                    || statement.getKey().startsWith("list") && analyzed.stream().anyMatch(FULL_SORT)) {
                auditSorts.add(statement.getKey());
            }
        }
        Path auditPlan = Path.of("target", "perf", "audit-plans.txt");
        Files.createDirectories(auditPlan.getParent());
        Files.writeString(auditPlan, String.join("\n", auditPlans) + "\n");

        String knownHash = TenantContext.runAs(tenantId, () -> one("SELECT content_sha256 FROM report WHERE content_sha256 IS NOT NULL LIMIT 1"));
        List<String> reportCheckPlans = new ArrayList<>();
        List<String> reportChecksOverBudget = new ArrayList<>();
        for (var lookup : Map.of("a file that matches", knownHash, "a file that matches nothing", "0".repeat(64)).entrySet()) {
            List<String> analyzed = null;
            for (int run = 0; run < 3; run++) {
                analyzed = TenantContext.runAs(tenantId,
                        () -> jdbc.sql("EXPLAIN (ANALYZE, BUFFERS) " + reportCheck(lookup.getValue())).query(String.class).list());
            }
            reportCheckPlans.add("-- " + lookup.getKey());
            reportCheckPlans.addAll(analyzed);
            if (buffers(analyzed) > REPORT_CHECK_BUFFER_BUDGET || analyzed.stream().noneMatch(l -> l.contains(REPORT_CHECK_INDEX))
                    || analyzed.stream().anyMatch(l -> l.matches(".*Seq Scan on report\\b.*"))) {
                reportChecksOverBudget.add(lookup.getKey() + ": " + buffers(analyzed) + " buffers");
            }
        }
        Path reportCheckPlan = Path.of("target", "perf", "report-check-plan.txt");
        Files.createDirectories(reportCheckPlan.getParent());
        Files.writeString(reportCheckPlan, String.join("\n", reportCheckPlans) + "\n");

        Path plan = Path.of("target", "perf", "work-queue-plan.txt");
        Files.createDirectories(plan.getParent());
        Files.writeString(plan, String.join("\n", workQueue) + "\n");

        String table = String.join("\n", report) + "\n\n| Job lookup (shared buffers touched, EXPLAIN ANALYZE) | Buffers |\n|---|---:|\n"
                + String.join("\n", lookupBuffers) + "\n";
        System.out.println(table);
        Path out = Path.of("target", "perf", "index-usage.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, table);
        assertThat(keptButIgnored).as("indexes of V008, V013, V016 and V018 the planner did not choose: drop them").isEmpty();
        assertThat(rejectedButChosen).as("rejected candidates the planner now chooses: add them to V008").isEmpty();
        assertThat(lookupsOverBudget).as("job lookups touching more than " + JOB_LOOKUP_BUFFER_BUDGET
                + " buffers: a lookup by one record must not walk the index of every tenant").isEmpty();
        assertThat(mappingsOverBudget).as("mappings of a large assessment reading more than " + MAPPINGS_BUFFER_BUDGET
                + " buffers: the current-mapping filter must not grow with mappings squared").isEmpty();
        assertThat(auditSorts).as("audit statements that scan the table, or lists that sort all matching rows (plans in target/perf/audit-plans.txt)")
                .isEmpty();
        assertThat(jobSequentialScans).as("statements that scan the job table sequentially: it only grows").isEmpty();
        assertThat(reportChecksOverBudget).as("report checks that do not use " + REPORT_CHECK_INDEX + " or read more than "
                + REPORT_CHECK_BUFFER_BUDGET + " buffers (plans in target/perf/report-check-plan.txt)").isEmpty();
        Path coveragePlan = Path.of("target", "perf", "coverage-plan.txt");
        Files.writeString(coveragePlan, String.join("\n", coverage) + "\n");
        assertThat(coverage).as("the coverage statement scans no hot table sequentially (plan in target/perf/coverage-plan.txt)")
                .noneMatch(line -> line.matches(".*Seq Scan on (mapping|evidence_span|evidence_response|document)\\b.*") && !line.contains("never executed"));
        assertThat(workQueue).as("the work queue statement scans no hot table sequentially (plan in target/perf/work-queue-plan.txt)")
                .noneMatch(line -> line.matches(".*Seq Scan on (mapping|review_event|job)\\b.*") && !line.contains("never executed"));
    }

    /** The audit list and export with real filters, as the application runs them; the tenant's entries come from row-level security. */
    private static Map<String, String> auditStatements() {
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.minusDays(30);
        var byAction = new AuditFilter("DETERMINATION_SIGNED", null, null, null, null, null).where();
        var byRange = new AuditFilter(null, null, null, null, from, to).where();
        var statements = new LinkedHashMap<String, String>();
        statements.put("list, no filter", AuditRepository.listSql(AuditFilter.NONE.where()));
        statements.put("list, one action", AuditRepository.listSql(byAction));
        statements.put("list, last 30 days", AuditRepository.listSql(byRange));
        statements.put("export, no filter", AuditRepository.exportSql(AuditFilter.NONE.where()));
        statements.put("export, one action", AuditRepository.exportSql(byAction));
        statements.put("export, last 30 days", AuditRepository.exportSql(byRange));
        statements.replaceAll((name, sql) -> sql.replace(":limit", "100").replace(":offset", "0")
                .replace(":action", "'DETERMINATION_SIGNED'").replace(":from", "'" + from + "'::timestamptz")
                .replace(":to", "'" + to + "'::timestamptz"));
        return statements;
    }

    /** What the scheduler runs: across tenants, under the transaction-local worker flag and no tenant. */
    private List<String> workerPlan(String explain) {
        return transaction.execute(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            return jdbc.sql(explain).query(String.class).list();
        });
    }

    /** Shared buffers (hit and read) the statement touched: the first {@code Buffers} line of the plan is the top node, children included. */
    private static int buffers(List<String> analyzedPlan) {
        return analyzedPlan.stream().map(l -> Pattern.compile("Buffers: shared (?:hit=(\\d+))?(?: ?read=(\\d+))?").matcher(l))
                .filter(Matcher::find)
                .mapToInt(m -> (m.group(1) == null ? 0 : Integer.parseInt(m.group(1))) + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2))))
                .findFirst().orElseThrow();
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
        String otherRuleVersion = one("SELECT id FROM rule_version OFFSET 1 LIMIT 1");
        String ruleSource = one("SELECT id FROM rule_source LIMIT 1");
        String reportHash = one("SELECT content_sha256 FROM report WHERE content_sha256 IS NOT NULL LIMIT 1");
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
                new Candidate("audit_log_newest_idx", "audit list, newest first (V001; D-403)",
                        AuditRepository.listSql(AuditFilter.NONE.where()).replace(":limit", "100").replace(":offset", "0")),
                new Candidate("assessment_program_status_finished_idx", "latest finished assessment of a program",
                        "SELECT id FROM assessment WHERE program_id = '" + program
                                + "' AND status = 'FINISHED' ORDER BY finished_at DESC, created_at DESC LIMIT 1"),
                new Candidate("rejected_change_event_status_created_idx", "open change events, newest first",
                        "SELECT id FROM change_event WHERE status = 'OPEN' ORDER BY created_at DESC", false),
                new Candidate("job_entity_idx", "ingest status of a document",
                        "SELECT id FROM job WHERE type = 'INGEST_DOCUMENT' AND entity_id = '" + document
                                + "' ORDER BY created_at DESC LIMIT 1"),
                new Candidate("job_entity_idx", "extraction status of a rule version",
                        "SELECT id FROM job WHERE type = 'EXTRACT_REQUIREMENTS' AND entity_id = '" + ruleVersion
                                + "' ORDER BY created_at DESC LIMIT 1"),
                new Candidate("job_entity_idx", "extraction status of every rule version (rule version list)",
                        "SELECT DISTINCT ON (entity_id) id FROM job WHERE type = 'EXTRACT_REQUIREMENTS' AND entity_id IN ('"
                                + ruleVersion + "', '" + otherRuleVersion + "') ORDER BY entity_id, created_at DESC"),
                Candidate.eitherOf("job_claim_idx", "job_entity_idx", "is a check of a rule source already queued (manual check: the queued jobs of V001 or the one source of V013)",
                        RuleSourceRepository.ENQUEUE.formatted("rs.id = '" + ruleSource + "'").replace(":createdBy", "NULL")),
                new Candidate("requirement_ordinal_key", "requirements of a rule version (existing unique index, so V008 adds none)",
                        "SELECT id FROM requirement WHERE rule_version_id = '" + ruleVersion + "' ORDER BY ordinal"),
                new Candidate(REPORT_CHECK_INDEX, "the report with the content of a checked file (V018)", reportCheck(reportHash)));
    }

    /** The report check statement as the application runs it, for one content hash. */
    private static String reportCheck(String sha256) {
        return ReportRepository.CHECK.replace(":h", "'" + sha256 + "'");
    }

    private String one(String sql) {
        return jdbc.sql(sql).query(String.class).single();
    }
}
