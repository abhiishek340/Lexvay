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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code findingCounts} builds "decided" from the shared {@code MappingRepository} predicates (D-362). Its result
 * must equal the statement it replaced, which spelled the test out, on the seeded tenant: as seeded, and after
 * every kind of review history and a stale mapping.
 */
class FindingCountsParityTest extends AbstractDatabaseTest {

    /** The statement before FIX-3, kept here only as the reference the shared predicates are compared with. */
    private static final String REFERENCE = """
            SELECT j.code AS jurisdiction, m.finding, count(*) AS n,
                   count(*) FILTER (WHERE le.kind IS NOT NULL AND le.kind <> 'REOPENED' AND NOT m.stale) AS decided
            FROM mapping m JOIN jurisdiction j ON j.id = m.jurisdiction_id JOIN requirement r ON r.id = m.requirement_id
            LEFT JOIN LATERAL (SELECT re.kind FROM review_event re WHERE re.mapping_id = m.id
                               ORDER BY re.created_at DESC, re.id DESC LIMIT 1) le ON true
            WHERE m.assessment_id = :id
            """ + MappingRepository.CURRENT + " GROUP BY j.code, m.finding ORDER BY j.code, m.finding";

    @Autowired SeedLoader loader;
    @Autowired AppUserRepository users;
    @Autowired AssessmentRepository assessments;
    @Autowired JdbcClient jdbc;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;
    @Autowired ObjectMapper json;

    @Test
    void theSharedPredicatesGiveTheSameCountsOnTheSeededTenant() throws Exception {
        ((FakeLlmClient) llm).reset();
        runner.runPending();
        var seed = SeedCopy.northfield("Finding Counts University", "finding-counts.example");
        SeedLoader.Result loaded = loader.load(seed, "demo-pass", ScriptedFakeModel.load(seed, llm, json)).get(0);
        UUID tenantId = loaded.tenantId();
        UUID reviewer = users.findForLogin("priya.raman@finding-counts.example").orElseThrow().id();

        assertThat(decided(tenantId, loaded.assessmentId())).as("the seed decides nothing").isZero();

        List<UUID> mappings = TenantContext.runAs(tenantId, () -> jdbc.sql(
                "SELECT id FROM mapping WHERE assessment_id = :a ORDER BY id").param("a", loaded.assessmentId()).query(UUID.class).list());
        for (int i = 0; i < mappings.size(); i++) {
            UUID mapping = mappings.get(i);
            switch (i % 7) {
                case 1 -> event(tenantId, mapping, "ACCEPTED", reviewer);
                case 2 -> event(tenantId, mapping, "GAP_CONFIRMED", reviewer);
                case 3 -> event(tenantId, mapping, "EVIDENCE_REQUESTED", reviewer);
                case 4 -> {
                    event(tenantId, mapping, "ACCEPTED", reviewer);
                    event(tenantId, mapping, "REOPENED", null);
                }
                case 5 -> {
                    event(tenantId, mapping, "ACCEPTED", reviewer);
                    TenantContext.runAs(tenantId, () -> jdbc.sql("UPDATE mapping SET stale = true WHERE id = :id").param("id", mapping).update());
                }
                case 6 -> {
                    event(tenantId, mapping, "EVIDENCE_REQUESTED", reviewer);
                    event(tenantId, mapping, "ACCEPTED", reviewer);
                }
                default -> {
                }
            }
        }

        assertThat(decided(tenantId, loaded.assessmentId())).as("the history above decides some mappings but not others")
                .isBetween(1L, (long) mappings.size() - 1);
    }

    /** Asserts both statements agree and returns the number of decided mappings. */
    private long decided(UUID tenantId, UUID assessmentId) {
        return TenantContext.runAs(tenantId, () -> {
            List<Map<String, Object>> reference = jdbc.sql(REFERENCE).param("id", assessmentId).query().listOfRows();
            List<Map<String, Object>> shared = assessments.findingCounts(assessmentId);
            assertThat(reference).isNotEmpty();
            assertThat(shared).isEqualTo(reference);
            return shared.stream().mapToLong(row -> ((Number) row.get("decided")).longValue()).sum();
        });
    }

    private void event(UUID tenantId, UUID mapping, String kind, UUID user) {
        TenantContext.runAs(tenantId, () -> jdbc.sql("INSERT INTO review_event (tenant_id, mapping_id, kind, note, user_id, created_at) "
                        + "VALUES (:t, :m, :k, 'Fixture.', :u, clock_timestamp())")
                .param("t", tenantId).param("m", mapping).param("k", kind).param("u", user).update());
    }
}
