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

    private static final String COLUMNS = "id, tenant_id, type, payload::text AS payload, status, attempts, locked_at, error, created_at";

    private static final RowMapper<Job> ROW = (rs, i) -> new Job(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            JobType.valueOf(rs.getString("type")),
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

    /** Enqueues a job for the current tenant. */
    public UUID enqueue(JobType type, Map<String, ?> payload) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO job (id, tenant_id, type, payload, created_by) VALUES (:id, :tenantId, :type, :payload, :createdBy)")
                .param("id", id)
                .param("tenantId", TenantContext.require())
                .param("type", type.name())
                .param("payload", json.writeValueAsString(payload), Types.OTHER)
                .param("createdBy", CurrentUser.get().map(u -> u.id()).orElse(null))
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

    /**
     * The newest job of a type whose payload names the given id. The key is part of the SQL text, not a bind
     * parameter, so the planner can match the expression index on {@code (payload->>'key')}.
     */
    public Optional<Job> latest(JobType type, String payloadKey, UUID payloadValue) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM job WHERE type = :type AND " + payloadField(payloadKey) + " = :value "
                        + "ORDER BY created_at DESC LIMIT 1")
                .param("type", type.name()).param("value", payloadValue.toString())
                .query(ROW).optional();
    }

    /** {@link #latest} for many ids in one statement; ids without a job are absent from the result. */
    public Map<UUID, Job> latestByPayloadValue(JobType type, String payloadKey, Collection<UUID> payloadValues) {
        if (payloadValues.isEmpty()) {
            return Map.of();
        }
        String field = payloadField(payloadKey);
        return jdbc.sql("SELECT DISTINCT ON (" + field + ") " + field + " AS payload_value, " + COLUMNS + " FROM job "
                        + "WHERE type = :type AND " + field + " IN (:values) ORDER BY " + field + ", created_at DESC")
                .param("type", type.name()).param("values", payloadValues.stream().map(UUID::toString).toList())
                .query((rs, i) -> Map.entry(UUID.fromString(rs.getString("payload_value")), ROW.mapRow(rs, i)))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Payload keys are code constants; anything but letters is refused so the key is safe inside SQL text. */
    private static String payloadField(String payloadKey) {
        if (!payloadKey.matches("[A-Za-z]+")) {
            throw new IllegalArgumentException("Invalid job payload key: " + payloadKey);
        }
        return "payload->>'" + payloadKey + "'";
    }
}
