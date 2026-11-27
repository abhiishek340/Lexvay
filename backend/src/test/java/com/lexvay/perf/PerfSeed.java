package com.lexvay.perf;

import com.lexvay.seed.ModelStrategy;
import com.lexvay.seed.SeedLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
     * assessments per program and signed or reopened determinations. Inserted as the database owner of the
     * test container (which bypasses row-level security) so that those tables are big enough for the planner
     * to consider an index. Synthetic; used only to check which indexes the planner chooses.
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
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("lexvay-perf-seed-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
