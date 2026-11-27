package com.lexvay.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.seed.ScriptedFakeModel;
import com.lexvay.seed.SeedCopy;
import com.lexvay.seed.SeedLoader;
import com.lexvay.tenant.AppUserRepository;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code latestAssessmentsUsing} reads each program's latest finished assessment through
 * {@link AssessmentRepository#LATEST_FINISHED} (D-450). Its result must equal the statement it replaced, which kept its
 * own correlated copy of the ordering, for every rule version of the seeded tenant and for programs whose history
 * separates the readings that matter: the newest finished assessment does not use the version, the newest assessment
 * failed, two finished assessments share a finish time.
 */
class LatestAssessmentsUsingParityTest extends AbstractDatabaseTest {

    /** The statement before FIX-7, kept here only as the reference the shared ordering is compared with. */
    private static final String REFERENCE = """
            SELECT DISTINCT a.id AS assessment_id, a.program_id, a.created_by
            FROM mapping m JOIN requirement r ON r.id = m.requirement_id JOIN assessment a ON a.id = m.assessment_id
            WHERE r.rule_version_id = :rv AND a.status = 'FINISHED'
              AND a.id = (SELECT a2.id FROM assessment a2 WHERE a2.program_id = a.program_id AND a2.status = 'FINISHED'
                          ORDER BY a2.finished_at DESC, a2.created_at DESC LIMIT 1)
            """;

    @Autowired SeedLoader loader;
    @Autowired AppUserRepository users;
    @Autowired MappingRepository mappings;
    @Autowired JdbcClient jdbc;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;
    @Autowired ObjectMapper json;

    @Test
    void theSharedOrderingGivesTheSameAssessmentsAsTheStatementItReplaced() throws Exception {
        runner.runPending();
        ((FakeLlmClient) llm).reset();
        var seed = SeedCopy.northfield("Latest Using University", "latest-using.example");
        SeedLoader.Result loaded = loader.load(seed, "demo-pass", ScriptedFakeModel.load(seed, llm, json)).get(0);
        UUID tenantId = loaded.tenantId();
        UUID reviewer = users.findForLogin("priya.raman@latest-using.example").orElseThrow().id();
        List<UUID> ruleVersions = TenantContext.runAs(tenantId, () -> jdbc.sql("SELECT id FROM rule_version ORDER BY id").query(UUID.class).list());
        assertThat(ruleVersions).as("the seed has rule versions to tell apart").hasSizeGreaterThan(1);
        UUID x = ruleVersions.get(0);
        UUID y = ruleVersions.get(1);

        OffsetDateTime base = OffsetDateTime.now().minusDays(1);
        // Newest finished assessment does not use x: only its older one does.
        UUID newestWithoutX = program(tenantId, "Newest without x");
        assessment(tenantId, newestWithoutX, "FINISHED", base, base, reviewer, x);
        UUID bNewest = assessment(tenantId, newestWithoutX, "FINISHED", base.plusHours(1), base.plusHours(1), reviewer, y);
        // Newest assessment failed (and finished later than the finished one): the older finished one counts.
        UUID failedNewest = program(tenantId, "Failed newest");
        UUID cFinished = assessment(tenantId, failedNewest, "FINISHED", base, base, reviewer, x);
        assessment(tenantId, failedNewest, "FAILED", base.plusHours(1), base.plusHours(1), reviewer, x, y);
        // Two finished assessments with the same finish time: the later created one counts.
        UUID tied = program(tenantId, "Tied finish");
        assessment(tenantId, tied, "FINISHED", base, base, reviewer, x);
        UUID dNewest = assessment(tenantId, tied, "FINISHED", base, base.plusMinutes(1), reviewer, y);

        for (UUID ruleVersion : ruleVersions) {
            Set<Map<String, Object>> reference = TenantContext.runAs(tenantId, () -> new HashSet<>(
                    jdbc.sql(REFERENCE).param("rv", ruleVersion).query().listOfRows()));
            Set<Map<String, Object>> shared = TenantContext.runAs(tenantId, () -> new HashSet<>(mappings.latestAssessmentsUsing(ruleVersion)));
            assertThat(shared).as("rule version %s", ruleVersion).isEqualTo(reference);
        }

        assertThat(assessmentIds(tenantId, x)).as("x: the finished assessment before a failure, not the programs whose newer finished one lacks x")
                .contains(cFinished).doesNotContain(bNewest, dNewest);
        assertThat(assessmentIds(tenantId, y)).as("y: the newest finished assessments that use it, not the failed one's program")
                .contains(bNewest, dNewest).doesNotContain(cFinished);
    }

    private Set<UUID> assessmentIds(UUID tenantId, UUID ruleVersion) {
        return TenantContext.runAs(tenantId, () -> {
            Set<UUID> ids = new HashSet<>();
            mappings.latestAssessmentsUsing(ruleVersion).forEach(row -> ids.add((UUID) row.get("assessment_id")));
            return ids;
        });
    }

    private UUID program(UUID tenantId, String name) {
        return TenantContext.runAs(tenantId, () -> jdbc.sql("INSERT INTO program (tenant_id, name, license_type, catalog_year) "
                + "VALUES (:t, :n, 'CLINICAL_MENTAL_HEALTH_COUNSELING', '2026-27') RETURNING id")
                .param("t", tenantId).param("n", name).query(UUID.class).single());
    }

    /** An assessment of the given status holding a mapping for every requirement of the given rule versions. */
    private UUID assessment(UUID tenantId, UUID program, String status, OffsetDateTime finishedAt, OffsetDateTime createdAt,
                            UUID createdBy, UUID... ruleVersions) {
        return TenantContext.runAs(tenantId, () -> {
            UUID id = jdbc.sql("INSERT INTO assessment (tenant_id, program_id, status, started_at, finished_at, llm_model, prompt_version, "
                            + "created_at, created_by) VALUES (:t, :p, :s, :f, :f, 'fixture', 'fixture', :c, :u) RETURNING id")
                    .param("t", tenantId).param("p", program).param("s", status).param("f", finishedAt).param("c", createdAt)
                    .param("u", createdBy).query(UUID.class).single();
            for (UUID ruleVersion : ruleVersions) {
                jdbc.sql("INSERT INTO mapping (tenant_id, assessment_id, requirement_id, jurisdiction_id, finding, method, rationale) "
                                + "SELECT :t, :a, r.id, v.jurisdiction_id, 'EVIDENCE_MISSING', 'RULES_ENGINE', 'Fixture.' "
                                + "FROM requirement r JOIN rule_version v ON v.id = r.rule_version_id WHERE v.id = :rv")
                        .param("t", tenantId).param("a", id).param("rv", ruleVersion).update();
            }
            return id;
        });
    }
}
