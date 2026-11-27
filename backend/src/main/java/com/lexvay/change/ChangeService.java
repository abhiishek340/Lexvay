package com.lexvay.change;

import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.assessment.MappingRepository;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spec section 7. A new rule version or course version creates a change event, reopens the signed
 * determinations that depend on the old version (D-029) and marks the mappings that cite it stale. The
 * re-run is a job; its preconditions are checked here so the caller gets a 409 instead of a failed job.
 */
@Service
public class ChangeService {

    public record RerunResult(UUID changeEventId, UUID jobId) {
    }

    private final ChangeEventRepository changes;
    private final MappingRepository mappings;
    private final JobRepository jobs;
    private final AuditService audit;
    private final JdbcClient jdbc;

    public ChangeService(ChangeEventRepository changes, MappingRepository mappings, JobRepository jobs, AuditService audit, JdbcClient jdbc) {
        this.changes = changes;
        this.mappings = mappings;
        this.jobs = jobs;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    /**
     * Called inside the transaction that creates the superseding rule version. That version comes from a person or
     * from a rule source check; a check has no signed-in user, so the event is then created by the system (null).
     */
    public UUID onRuleVersionSuperseded(UUID oldRuleVersionId, UUID newRuleVersionId, String newLabel) {
        UUID actor = CurrentUser.idOrNull();
        UUID eventId = changes.insert(TenantContext.require(), ChangeKind.RULE_VERSION, oldRuleVersionId, newRuleVersionId, actor);
        List<UUID> reopened = jdbc.sql("UPDATE determination SET status = 'REOPENED' WHERE rule_version_id = :old AND status = 'SIGNED' RETURNING id")
                .param("old", oldRuleVersionId).query(UUID.class).list();
        for (UUID determinationId : reopened) {
            audit.record("DETERMINATION_REOPENED", "determination", determinationId, Map.of("changeEventId", eventId.toString(), "reason", "New rule version: " + newLabel));
        }
        int stale = mappings.markStaleByRuleVersion(oldRuleVersionId);
        audit.record("CHANGE_EVENT_CREATED", "change_event", eventId, Map.of("kind", "RULE_VERSION", "oldId", oldRuleVersionId.toString(),
                "newId", newRuleVersionId.toString(), "reopenedDeterminations", reopened.size(), "staleMappings", stale));
        return eventId;
    }

    /** Called inside the transaction that creates the new course version. */
    public UUID onCourseVersionAdded(UUID oldCourseVersionId, UUID newCourseVersionId, String courseCode) {
        UUID actor = CurrentUser.require().id();
        UUID eventId = changes.insert(TenantContext.require(), ChangeKind.COURSE_VERSION, oldCourseVersionId, newCourseVersionId, actor);
        List<UUID> reopened = jdbc.sql("""
                UPDATE determination d SET status = 'REOPENED' WHERE d.status = 'SIGNED'
                  AND EXISTS (SELECT 1 FROM determination_course_version dcv WHERE dcv.determination_id = d.id AND dcv.course_version_id = :old)
                RETURNING d.id
                """).param("old", oldCourseVersionId).query(UUID.class).list();
        for (UUID determinationId : reopened) {
            audit.record("DETERMINATION_REOPENED", "determination", determinationId, Map.of("changeEventId", eventId.toString(), "reason", "New course version: " + courseCode));
        }
        int stale = mappings.markStaleByCourseVersion(oldCourseVersionId);
        audit.record("CHANGE_EVENT_CREATED", "change_event", eventId, Map.of("kind", "COURSE_VERSION", "oldId", oldCourseVersionId.toString(),
                "newId", newCourseVersionId.toString(), "reopenedDeterminations", reopened.size(), "staleMappings", stale));
        return eventId;
    }

    @Transactional(readOnly = true)
    public List<ChangeEventView> list() {
        return changes.findViews();
    }

    @Transactional
    public RerunResult rerun(UUID changeEventId) {
        ChangeEventView view = changes.findViewById(changeEventId).orElseThrow(() -> new NotFoundException("Change event not found"));
        if (view.status() == ChangeStatus.HANDLED) {
            throw new ConflictException("This change has already been re-run");
        }
        if (view.kind() == ChangeKind.RULE_VERSION && !view.newApproved()) {
            throw new ConflictException("Approve the new rule version's requirement list before re-running");
        }
        if (view.kind() == ChangeKind.COURSE_VERSION && !view.newVersionHasDocument()) {
            throw new ConflictException("Upload a document for the new course version before re-running");
        }
        UUID jobId = jobs.enqueue(JobType.RERUN_CHANGE, Map.of("changeEventId", changeEventId.toString()));
        audit.record("CHANGE_RERUN_REQUESTED", "change_event", changeEventId, Map.of("kind", view.kind().name(), "jobId", jobId.toString()));
        return new RerunResult(changeEventId, jobId);
    }
}
