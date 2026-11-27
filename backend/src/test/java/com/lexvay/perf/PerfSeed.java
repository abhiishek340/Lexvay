package com.lexvay.perf;

import com.lexvay.seed.ModelStrategy;
import com.lexvay.seed.SeedLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Fills the database with more tenants than the demo has: the fictional seed loaded again and again under
 * other tenant names and e-mail addresses, so planner statistics and row-level security see realistic volume.
 */
final class PerfSeed {

    private PerfSeed() {
    }

    /** Loads {@code copies} renamed copies of the seed through the real services (about a second each). */
    static void loadCopies(SeedLoader loader, Path seedDir, String password, ModelStrategy model, int copies) {
        Path work = createTempDir();
        try {
            String manifest = Files.readString(seedDir.resolve("manifest.yml"), StandardCharsets.UTF_8);
            for (String shared : List.of("pdf", "rules", "fake-llm.yml")) {
                Files.createSymbolicLink(work.resolve(shared), seedDir.toAbsolutePath().resolve(shared));
            }
            IntStream.rangeClosed(1, copies).forEach(n -> {
                String renamed = manifest
                        .replace("name: Northfield University", "name: Northfield University " + n)
                        .replace("name: Westbrook College", "name: Westbrook College " + n)
                        .replace("@northfield.example", "." + n + "@northfield.example")
                        .replace("@westbrook.example", "." + n + "@westbrook.example");
                try {
                    Files.writeString(work.resolve("manifest.yml"), renamed, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                loader.load(work, password, model);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Rows the seed never produces but a tenant that has used the product for a while has: repeated
     * assessments per program, signed or reopened determinations and a review history on most mappings
     * (none, an open evidence request, a reopening, or a request answered and accepted), and the finished jobs
     * that pile up because jobs are never deleted (re-ingests, extractions, source checks). Inserted as the
     * database owner of the test container (which bypasses row-level security) so that those tables are big
     * enough for the planner to consider an index. Synthetic; used only to check which indexes the planner chooses.
     */
    static void addHistoryVolume(Statement admin) throws SQLException {
        admin.execute("""
                INSERT INTO assessment (tenant_id, program_id, status, started_at, finished_at, llm_model, prompt_version)
                SELECT p.tenant_id, p.id, CASE WHEN g % 5 = 0 THEN 'FAILED' ELSE 'FINISHED' END,
                       now() - g * interval '1 day', now() - g * interval '1 day', 'fake', 'match-v1'
                FROM program p CROSS JOIN generate_series(1, 20) g
                """);
        admin.execute("""
                INSERT INTO determination (tenant_id, program_id, jurisdiction_id, rule_version_id, assessment_id, outcome,
                                           signed_by, status, versions_hash)
                SELECT a.tenant_id, a.program_id, rv.jurisdiction_id, rv.id, a.id, 'MEETS', u.id,
                       CASE WHEN g % 4 = 0 THEN 'REOPENED' ELSE 'SIGNED' END, repeat('ab', 32)
                FROM assessment a
                JOIN rule_version rv ON rv.tenant_id = a.tenant_id
                CROSS JOIN generate_series(1, 2) g
                JOIN LATERAL (SELECT id FROM app_user WHERE tenant_id = a.tenant_id LIMIT 1) u ON true
                """);
        admin.execute("""
                INSERT INTO job (tenant_id, type, entity_id, payload, status, created_at)
                SELECT d.tenant_id, 'INGEST_DOCUMENT', d.id, jsonb_build_object('documentId', d.id), 'DONE', now() - g * interval '1 hour'
                FROM document d CROSS JOIN generate_series(1, 20) g
                UNION ALL
                SELECT rv.tenant_id, 'EXTRACT_REQUIREMENTS', rv.id, jsonb_build_object('ruleVersionId', rv.id), 'DONE', now() - g * interval '1 hour'
                FROM rule_version rv CROSS JOIN generate_series(1, 20) g
                UNION ALL
                SELECT rs.tenant_id, 'CHECK_RULE_SOURCE', rs.id, jsonb_build_object('ruleSourceId', rs.id), 'DONE', now() - g * interval '1 day'
                FROM rule_source rs CROSS JOIN generate_series(1, 30) g
                """);
        admin.execute("""
                INSERT INTO review_event (tenant_id, mapping_id, kind, note, user_id, created_at)
                SELECT m.tenant_id, m.id, k.kind, 'Synthetic history.', u.id, now() - (4 - k.n) * interval '1 hour'
                FROM mapping m
                JOIN LATERAL (SELECT id FROM app_user WHERE tenant_id = m.tenant_id LIMIT 1) u ON true
                CROSS JOIN (VALUES (1, 'EVIDENCE_REQUESTED'), (2, 'REOPENED'), (3, 'ACCEPTED')) k(n, kind)
                WHERE k.n <= CASE WHEN abs(hashtext(m.id::text)) % 10 < 3 THEN 0
                                  WHEN abs(hashtext(m.id::text)) % 10 < 6 THEN 3
                                  WHEN abs(hashtext(m.id::text)) % 10 < 8 THEN 1 ELSE 2 END
                """);
    }

    /** Two large finished assessments of one program, see {@link #addLargeAssessments}. */
    record LargeAssessments(UUID beforeRerun, UUID afterRerun) {
    }

    /**
     * What a program that re-ran an assessment on a superseded rule version holds, at a size well above the demo:
     * rule version {@code v1} with 500 requirements and its successor {@code v2} with 500 more, and, for the first
     * program of {@code tenantId}, an assessment with 500 mappings on {@code v1} before the re-run (every mapping is
     * current, though a successor exists) and one with 500 mappings on {@code v1} and 500 on {@code v2} after it
     * (the first 500 are hidden by D-066). Inserted as the database owner, like {@link #addHistoryVolume}.
     */
    static LargeAssessments addLargeAssessments(Statement admin, UUID tenantId) throws SQLException {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        UUID beforeRerun = UUID.randomUUID();
        UUID afterRerun = UUID.randomUUID();
        UUID program;
        UUID jurisdiction;
        UUID span;
        try (ResultSet row = admin.executeQuery("""
                SELECT (SELECT id FROM program WHERE tenant_id = '%1$s' ORDER BY created_at LIMIT 1),
                       (SELECT jurisdiction_id FROM rule_version WHERE tenant_id = '%1$s' ORDER BY created_at LIMIT 1),
                       (SELECT id FROM evidence_span WHERE tenant_id = '%1$s' ORDER BY created_at LIMIT 1)
                """.formatted(tenantId))) {
            row.next();
            program = row.getObject(1, UUID.class);
            jurisdiction = row.getObject(2, UUID.class);
            span = row.getObject(3, UUID.class);
        }
        admin.execute("""
                INSERT INTO rule_version (id, tenant_id, jurisdiction_id, license_type, label, source_url, effective_from,
                                          supersedes_id, document_id)
                SELECT v.id, rv.tenant_id, rv.jurisdiction_id, rv.license_type, v.label, rv.source_url, rv.effective_from,
                       v.supersedes_id, rv.document_id
                FROM (SELECT * FROM rule_version WHERE tenant_id = '%1$s' ORDER BY created_at LIMIT 1) rv
                CROSS JOIN (VALUES ('%2$s'::uuid, 'Perf rule set v1', NULL::uuid), ('%3$s'::uuid, 'Perf rule set v2', '%2$s'::uuid))
                       v(id, label, supersedes_id)
                ORDER BY v.label
                """.formatted(tenantId, v1, v2));
        admin.execute("""
                INSERT INTO requirement (tenant_id, rule_version_id, ref, title, text, type, ordinal)
                SELECT '%1$s', v.id, 'PERF-' || g, 'Perf requirement ' || g, 'Coursework in perf topic ' || g, 'SUBJECT', g
                FROM (VALUES ('%2$s'::uuid), ('%3$s'::uuid)) v(id) CROSS JOIN generate_series(1, 500) g
                """.formatted(tenantId, v1, v2));
        admin.execute("""
                INSERT INTO assessment (id, tenant_id, program_id, status, started_at, finished_at, llm_model, prompt_version)
                VALUES ('%2$s', '%1$s', '%4$s', 'FINISHED', now(), now(), 'fake', 'match-v1'),
                       ('%3$s', '%1$s', '%4$s', 'FINISHED', now(), now(), 'fake', 'match-v1')
                """.formatted(tenantId, beforeRerun, afterRerun, program));
        admin.execute("""
                INSERT INTO mapping (tenant_id, assessment_id, requirement_id, jurisdiction_id, finding, method, rationale, evidence_span_id)
                SELECT '%1$s', a.id, r.id, '%2$s', CASE WHEN r.ordinal %% 5 < 3 THEN 'EVIDENCE_FOUND' ELSE 'EVIDENCE_MISSING' END,
                       'MATCH', 'Synthetic mapping.', CASE WHEN r.ordinal %% 5 < 3 THEN '%3$s'::uuid END
                FROM requirement r
                JOIN (VALUES ('%4$s'::uuid, '%5$s'::uuid), ('%6$s'::uuid, '%5$s'::uuid), ('%6$s'::uuid, '%7$s'::uuid)) a(id, rule_version_id)
                  ON r.rule_version_id = a.rule_version_id
                """.formatted(tenantId, jurisdiction, span, beforeRerun, v1, afterRerun, v2));
        return new LargeAssessments(beforeRerun, afterRerun);
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("lexvay-perf-seed-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
