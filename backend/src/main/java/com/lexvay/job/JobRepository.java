package com.lexvay.job;

import com.lexvay.auth.CurrentUser;
import com.lexvay.common.TenantContext;
import java.sql.Types;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JobRepository {

    private static final String COLUMNS = "id, tenant_id, type, entity_id, payload::text AS payload, status, attempts, locked_at, error, created_at";

    private static final RowMapper<Job> ROW = (rs, i) -> new Job(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            JobType.valueOf(rs.getString("type")),
            rs.getObject("entity_id", UUID.class),
            rs.getString("payload"),
            JobStatus.valueOf(rs.getString("status")),
            rs.getInt("attempts"),
            rs.getObject("locked_at", java.time.OffsetDateTime.class),
            rs.getString("error"),
            rs.getObject("created_at", java.time.OffsetDateTime.class));

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JobRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Enqueues a job for the current tenant, working on the record {@code entityId}; the payload carries its input. */
    public UUID enqueue(JobType type, UUID entityId, Map<String, ?> payload) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO job (id, tenant_id, type, entity_id, payload, created_by) "
                        + "VALUES (:id, :tenantId, :type, :entityId, :payload, :createdBy)")
                .param("id", id)
                .param("tenantId", TenantContext.require())
                .param("type", type.name())
                .param("entityId", entityId)
                .param("payload", json.writeValueAsString(payload), Types.OTHER)
                .param("createdBy", CurrentUser.idOrNull())
                .update();
        return id;
    }

    /**
     * Claims the oldest queued job (or one left RUNNING for over ten minutes) across all tenants. The
     * worker flag is transaction-local and honoured only by the job table's policies (D-013).
     */
    @Transactional
    public Optional<Job> claim() {
        jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
        return jdbc.sql("""
                UPDATE job SET status = 'RUNNING', locked_at = now(), attempts = attempts + 1
                WHERE id = (
                    SELECT id FROM job
                    WHERE status = 'QUEUED' OR (status = 'RUNNING' AND locked_at < now() - interval '10 minutes')
                    ORDER BY created_at
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED)
                RETURNING """ + " " + COLUMNS)
                .query(ROW).optional();
    }

    /** Jobs waiting to be claimed, across all tenants, from the claim index. For the queue depth gauge. */
    @Transactional(readOnly = true)
    public long queuedCount() {
        jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
        return jdbc.sql("SELECT count(*) FROM job WHERE status = 'QUEUED'").query(Long.class).single();
    }

    /** The following run under the job's tenant context. */
    public void markDone(UUID id) {
        jdbc.sql("UPDATE job SET status = 'DONE', error = NULL WHERE id = :id").param("id", id).update();
    }

    public void markFailed(UUID id, String error) {
        jdbc.sql("UPDATE job SET status = 'FAILED', error = :error WHERE id = :id")
                .param("error", error).param("id", id).update();
    }

    public void requeue(UUID id, String error) {
        jdbc.sql("UPDATE job SET status = 'QUEUED', locked_at = NULL, error = :error WHERE id = :id")
                .param("error", error).param("id", id).update();
    }

    public Optional<Job> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM job WHERE id = :id").param("id", id).query(ROW).optional();
    }

    /** The newest job of a type working on the given record, from {@code job_entity_idx}. */
    public Optional<Job> latest(JobType type, UUID entityId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM job WHERE type = :type AND entity_id = :entityId "
                        + "ORDER BY created_at DESC LIMIT 1")
                .param("type", type.name()).param("entityId", entityId)
                .query(ROW).optional();
    }

    /** {@link #latest} for many records in one statement; records without a job are absent from the result. */
    public Map<UUID, Job> latestByEntity(JobType type, Collection<UUID> entityIds) {
        if (entityIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("SELECT DISTINCT ON (entity_id) " + COLUMNS + " FROM job "
                        + "WHERE type = :type AND entity_id IN (:entityIds) ORDER BY entity_id, created_at DESC")
                .param("type", type.name()).param("entityIds", entityIds)
                .query(ROW).list().stream().collect(Collectors.toMap(Job::entityId, job -> job));
    }
}
