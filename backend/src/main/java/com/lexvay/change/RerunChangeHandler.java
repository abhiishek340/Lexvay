package com.lexvay.change;

import com.lexvay.assessment.EvidenceSpanRepository;
import com.lexvay.assessment.Mapping;
import com.lexvay.assessment.MappingProducer;
import com.lexvay.assessment.MappingRepository;
import com.lexvay.assessment.rules.RulesEngine;
import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import com.lexvay.document.Document;
import com.lexvay.document.DocumentPage;
import com.lexvay.document.DocumentRepository;
import com.lexvay.document.QuoteVerifier;
import com.lexvay.job.Job;
import com.lexvay.job.JobHandler;
import com.lexvay.job.JobType;
import com.lexvay.job.PermanentJobFailure;
import com.lexvay.program.CourseRepository;
import com.lexvay.program.CourseVersion;
import com.lexvay.review.ReviewKind;
import com.lexvay.review.ReviewRepository;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.Requirement;
import com.lexvay.rule.RuleVersion;
import com.lexvay.rule.RuleVersionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The "re-run" action for a change event (spec section 7; D-015, D-028). */
@Component
public class RerunChangeHandler implements JobHandler {

    private final ChangeEventRepository changes;
    private final MappingRepository mappings;
    private final ReviewRepository reviews;
    private final RuleVersionRepository ruleVersions;
    private final CourseRepository courses;
    private final DocumentRepository documents;
    private final EvidenceSpanRepository spans;
    private final MappingProducer producer;
    private final AuditService audit;

    public RerunChangeHandler(ChangeEventRepository changes, MappingRepository mappings, ReviewRepository reviews,
                              RuleVersionRepository ruleVersions, CourseRepository courses, DocumentRepository documents,
                              EvidenceSpanRepository spans, MappingProducer producer, AuditService audit) {
        this.changes = changes;
        this.mappings = mappings;
        this.reviews = reviews;
        this.ruleVersions = ruleVersions;
        this.courses = courses;
        this.documents = documents;
        this.spans = spans;
        this.producer = producer;
        this.audit = audit;
    }

    @Override
    public JobType type() {
        return JobType.RERUN_CHANGE;
    }

    @Override
    public void handle(Job job, Map<String, Object> payload) {
        UUID eventId = UUID.fromString(String.valueOf(payload.get("changeEventId")));
        ChangeEvent event = changes.findById(eventId).orElseThrow(() -> new PermanentJobFailure("Change event " + eventId + " not found"));
        if (event.status() == ChangeStatus.HANDLED) {
            return;
        }
        Map<String, Object> summary = event.kind() == ChangeKind.RULE_VERSION ? rerunRuleVersion(event) : rerunCourseVersion(event);
        changes.markHandled(eventId);
        Map<String, Object> detail = new HashMap<>(summary);
        detail.put("jobId", job.id().toString());
        detail.put("kind", event.kind().name());
        audit.recordAs(null, "CHANGE_RERUN", "change_event", eventId, detail);
    }

    /** New requirements: carry over unchanged ones with their decision, evaluate changed or new ones afresh. */
    private Map<String, Object> rerunRuleVersion(ChangeEvent event) {
        RuleVersion oldVersion = ruleVersions.findById(event.oldId()).orElseThrow(() -> new PermanentJobFailure("Old rule version missing"));
        RuleVersion newVersion = ruleVersions.findById(event.newId()).orElseThrow(() -> new PermanentJobFailure("New rule version missing"));
        if (!newVersion.approved()) {
            throw new PermanentJobFailure("The new rule version is not approved");
        }
        Map<String, Requirement> oldByRef = new HashMap<>();
        for (Requirement r : ruleVersions.requirements(oldVersion.id())) {
            oldByRef.put(r.ref(), r);
        }
        List<Requirement> newRequirements = ruleVersions.requirements(newVersion.id());
        UUID tenantId = TenantContext.require();
        int carried = 0;
        int reevaluated = 0;
        int assessments = 0;
        for (Map<String, Object> target : mappings.latestAssessmentsUsing(oldVersion.id())) {
            assessments++;
            UUID assessmentId = (UUID) target.get("assessment_id");
            UUID programId = (UUID) target.get("program_id");
            UUID actor = (UUID) target.get("created_by");
            Map<NumericField, RulesEngine.Fact> facts = producer.currentFacts(programId);
            for (Requirement requirement : newRequirements) {
                if (mappings.exists(assessmentId, requirement.id())) {
                    continue;
                }
                Requirement previous = oldByRef.get(requirement.ref());
                Optional<Mapping> oldMapping = previous == null ? Optional.empty() : mappings.findByAssessmentAndRequirement(assessmentId, previous.id());
                if (previous != null && oldMapping.isPresent() && unchanged(previous, requirement)) {
                    Mapping old = oldMapping.get();
                    mappings.insertIfAbsent(tenantId, assessmentId, requirement.id(), newVersion.jurisdictionId(), old.finding(), old.method(),
                            old.rationale(), old.evidence() == null ? null : old.evidence().spanId(), actor);
                    if (old.decision() != null) {
                        UUID newMappingId = mappings.findByAssessmentAndRequirement(assessmentId, requirement.id()).orElseThrow().id();
                        String note = "Carried over from " + oldVersion.label() + (old.decision().note() == null ? "" : ": " + old.decision().note());
                        reviews.insert(tenantId, newMappingId, ReviewKind.valueOf(old.decision().kind()), note, old.decision().userId());
                    }
                    carried++;
                } else {
                    MappingProducer.Produced p = producer.produce(assessmentId, programId, requirement, facts, actor);
                    mappings.insertIfAbsent(tenantId, assessmentId, requirement.id(), newVersion.jurisdictionId(), p.finding(), p.method(),
                            p.rationale(), p.evidenceSpanId(), actor);
                    oldMapping.ifPresent(old -> reviews.insert(tenantId, old.id(), ReviewKind.REOPENED,
                            "Requirement " + requirement.ref() + " changed in " + newVersion.label(), null));
                    reevaluated++;
                }
            }
        }
        return Map.of("assessments", assessments, "carriedOver", carried, "reevaluated", reevaluated);
    }

    static boolean unchanged(Requirement a, Requirement b) {
        return a.type() == b.type()
                && QuoteVerifier.normalizeWhitespace(a.text()).equals(QuoteVerifier.normalizeWhitespace(b.text()))
                && a.numericField() == b.numericField()
                && Objects.equals(a.numericOperator(), b.numericOperator())
                && (a.numericValue() == null ? b.numericValue() == null : b.numericValue() != null && a.numericValue().compareTo(b.numericValue()) == 0);
    }

    /** Re-verify each stale quote against the new course version's documents. */
    private Map<String, Object> rerunCourseVersion(ChangeEvent event) {
        CourseVersion newVersion = courses.findVersion(event.newId()).orElseThrow(() -> new PermanentJobFailure("New course version missing"));
        String courseCode = courses.findById(newVersion.courseId()).map(c -> c.code()).orElse("course");
        List<Document> newDocuments = documents.findByCourseVersion(newVersion.id());
        if (newDocuments.isEmpty()) {
            throw new PermanentJobFailure("The new course version has no documents yet");
        }
        Map<Document, List<DocumentPage>> pagesByDocument = new HashMap<>();
        for (Document d : newDocuments) {
            pagesByDocument.put(d, documents.pages(d.id()));
        }
        UUID tenantId = TenantContext.require();
        int kept = 0;
        int returned = 0;
        for (Mapping mapping : mappings.findStaleCitingCourseVersion(event.oldId())) {
            boolean found = false;
            for (var entry : pagesByDocument.entrySet()) {
                for (DocumentPage page : entry.getValue()) {
                    Optional<QuoteVerifier.Match> match = QuoteVerifier.find(page.text(), mapping.evidence().quote());
                    if (match.isPresent()) {
                        UUID spanId = spans.insert(tenantId, entry.getKey().id(), page.pageNumber(), match.get().charStart(),
                                match.get().charEnd(), match.get().exactText(), null);
                        mappings.updateEvidence(mapping.id(), spanId);
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
            if (found) {
                kept++;
            } else {
                String reason = "The quoted passage no longer appears in " + courseCode + " (" + newVersion.catalogYear() + "); the earlier evidence cited an outdated version";
                mappings.updateToMissing(mapping.id(), reason);
                reviews.insert(tenantId, mapping.id(), ReviewKind.REOPENED, reason, null);
                returned++;
            }
        }
        return Map.of("courseCode", courseCode, "quotesStillPresent", kept, "returnedToReview", returned);
    }
}
