package com.lexvay.review;

import com.lexvay.assessment.AssessmentRepository;
import com.lexvay.assessment.Mapping;
import com.lexvay.assessment.MappingRepository;
import com.lexvay.assessment.ProgramCitationService;
import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.auth.LexvayUser;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.determination.DeterminationRepository;
import com.lexvay.program.ProgramService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The evidence request loop: a reviewer asks (an EVIDENCE_REQUESTED review event), a person answers with a
 * verified passage, and the requirement returns to review. The answer is attached to the mapping; the proposed
 * finding, method, rationale and cited span of the mapping are never touched (product rules 1 and 5).
 */
@Service
public class EvidenceRequestService {

    private final MappingRepository mappings;
    private final AssessmentRepository assessments;
    private final ReviewRepository reviews;
    private final EvidenceResponseRepository responses;
    private final EvidenceRequestRepository requests;
    private final DeterminationRepository determinations;
    private final ProgramCitationService citations;
    private final ProgramService programs;
    private final AuditService audit;

    public EvidenceRequestService(MappingRepository mappings, AssessmentRepository assessments, ReviewRepository reviews,
                                  EvidenceResponseRepository responses, EvidenceRequestRepository requests,
                                  DeterminationRepository determinations, ProgramCitationService citations,
                                  ProgramService programs, AuditService audit) {
        this.mappings = mappings;
        this.assessments = assessments;
        this.reviews = reviews;
        this.responses = responses;
        this.requests = requests;
        this.determinations = determinations;
        this.citations = citations;
        this.programs = programs;
        this.audit = audit;
    }

    @Transactional
    public EvidenceResponseView respond(UUID mappingId, EvidenceResponseRequest request) {
        Mapping mapping = mappings.findById(mappingId).orElseThrow(() -> new NotFoundException("Mapping not found"));
        if (mapping.stale()) {
            throw new ConflictException("This mapping cites an outdated rule or course version; re-run matching before answering");
        }
        if (determinations.hasSigned(mapping.assessmentId(), mapping.jurisdictionId())) {
            throw new ConflictException("A signed determination covers this mapping; it must be reopened by a change before answering");
        }
        ReviewRepository.LatestEvent latest = reviews.latest(mappingId).orElse(null);
        if (latest == null || latest.kind() != ReviewKind.EVIDENCE_REQUESTED) {
            throw new ConflictException("No open evidence request for this requirement");
        }
        UUID programId = assessments.findById(mapping.assessmentId()).orElseThrow().programId();
        LexvayUser actor = CurrentUser.require();
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
        ProgramCitationService.Citation citation = citations.cite(programId, request.documentId(), request.pageNumber(),
                request.quote(), actor.id());

        UUID tenantId = TenantContext.require();
        UUID responseId = responses.insert(tenantId, mappingId, latest.id(), citation.spanId(), comment, actor.id());
        reviews.insert(tenantId, mappingId, ReviewKind.REOPENED, "Evidence provided by " + actor.displayName(), actor.id());
        audit.record("EVIDENCE_PROVIDED", "mapping", mappingId, Map.of(
                "evidenceResponseId", responseId.toString(), "requirementRef", mapping.requirement().ref(),
                "jurisdiction", mapping.jurisdiction(), "documentId", citation.document().id().toString(),
                "pageNumber", citation.page().pageNumber(), "quote", citation.match().exactText()));
        return responses.findById(responseId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponseView> responses(UUID mappingId) {
        mappings.findById(mappingId).orElseThrow(() -> new NotFoundException("Mapping not found"));
        return responses.findByMapping(mappingId);
    }

    /** Requests of the program's latest finished assessment; none when no assessment has finished. */
    @Transactional(readOnly = true)
    public List<EvidenceRequestView> requests(UUID programId, EvidenceRequestStatus status) {
        programs.get(programId);
        return assessments.latestFinished(programId)
                .map(assessment -> requests.findByAssessment(assessment.id(), status))
                .orElse(List.of());
    }
}
