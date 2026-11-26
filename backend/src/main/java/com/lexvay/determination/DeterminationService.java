package com.lexvay.determination;

import com.lexvay.assessment.Assessment;
import com.lexvay.assessment.AssessmentRepository;
import com.lexvay.assessment.Mapping;
import com.lexvay.assessment.MappingRepository;
import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.ConflictException;
import com.lexvay.common.TenantContext;
import com.lexvay.program.Program;
import com.lexvay.program.ProgramService;
import com.lexvay.rule.Jurisdiction;
import com.lexvay.rule.JurisdictionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signing a jurisdiction-level determination (product rules 1 and 6). Refused with 409 unless every
 * mapping for that jurisdiction in the latest finished assessment is current and has a human decision.
 */
@Service
public class DeterminationService {

    private final DeterminationRepository determinations;
    private final AssessmentRepository assessments;
    private final MappingRepository mappings;
    private final ProgramService programs;
    private final JurisdictionRepository jurisdictions;
    private final AuditService audit;

    public DeterminationService(DeterminationRepository determinations, AssessmentRepository assessments,
                                MappingRepository mappings, ProgramService programs, JurisdictionRepository jurisdictions,
                                AuditService audit) {
        this.determinations = determinations;
        this.assessments = assessments;
        this.mappings = mappings;
        this.programs = programs;
        this.jurisdictions = jurisdictions;
        this.audit = audit;
    }

    @Transactional
    public Determination sign(UUID programId, SignRequest request) {
        Program program = programs.get(programId);
        if (!program.jurisdictions().contains(request.jurisdiction())) {
            throw new IllegalArgumentException("Jurisdiction " + request.jurisdiction() + " is not a target of this program");
        }
        Jurisdiction jurisdiction = jurisdictions.findByCodes(List.of(request.jurisdiction())).get(0);
        Assessment assessment = assessments.latestFinished(programId)
                .orElseThrow(() -> new ConflictException("No finished assessment exists for this program"));
        long total = mappings.countByJurisdiction(assessment.id(), jurisdiction.id());
        if (total == 0) {
            throw new ConflictException("The latest assessment has no mappings for " + request.jurisdiction());
        }
        long undecided = mappings.countUndecided(assessment.id(), jurisdiction.id());
        if (undecided > 0) {
            throw new ConflictException(undecided + " of " + total + " requirements for " + request.jurisdiction()
                    + " still need a review decision or cite an outdated version");
        }
        if (determinations.hasSignedForProgram(programId, jurisdiction.id())) {
            throw new ConflictException("A signed determination already exists for " + request.jurisdiction()
                    + "; it is reopened only by a rule or course change");
        }
        List<Mapping> jurisdictionMappings = mappings.findByAssessmentAndJurisdiction(assessment.id(), request.jurisdiction());
        UUID ruleVersionId = jurisdictionMappings.get(0).ruleVersionId();
        List<UUID> courseVersionIds = determinations.currentCourseVersionIds(programId);
        String hash = VersionsHash.compute(ruleVersionId, courseVersionIds);
        UUID signer = CurrentUser.require().id();
        UUID id = UUID.randomUUID();
        determinations.insert(id, TenantContext.require(), programId, jurisdiction.id(), ruleVersionId, assessment.id(),
                request.outcome(), signer, hash, courseVersionIds);
        audit.record("DETERMINATION_SIGNED", "determination", id, Map.of(
                "programId", programId.toString(), "jurisdiction", request.jurisdiction(), "outcome", request.outcome().name(),
                "ruleVersionId", ruleVersionId.toString(), "assessmentId", assessment.id().toString(),
                "courseVersionCount", courseVersionIds.size(), "versionsHash", hash, "requirementCount", total));
        return determinations.findById(id).orElseThrow();
    }

    public List<Determination> list(UUID programId) {
        programs.get(programId);
        return determinations.findByProgram(programId);
    }
}
