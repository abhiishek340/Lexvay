package com.lexvay.review;

import com.lexvay.assessment.Mapping;
import com.lexvay.assessment.MappingRepository;
import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.determination.DeterminationRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Human decisions on proposed mappings. The system never writes ACCEPTED or GAP_CONFIRMED itself. */
@Service
public class ReviewService {

    private final MappingRepository mappings;
    private final ReviewRepository reviews;
    private final DeterminationRepository determinations;
    private final AuditService audit;

    public ReviewService(MappingRepository mappings, ReviewRepository reviews, DeterminationRepository determinations,
                         AuditService audit) {
        this.mappings = mappings;
        this.reviews = reviews;
        this.determinations = determinations;
        this.audit = audit;
    }

    @Transactional
    public Mapping review(UUID mappingId, ReviewRequest request) {
        Mapping mapping = mappings.findById(mappingId).orElseThrow(() -> new NotFoundException("Mapping not found"));
        if (request.kind() == ReviewKind.REOPENED) {
            throw new IllegalArgumentException("REOPENED is recorded by the system when a rule or course version changes");
        }
        String note = request.note() == null ? null : request.note().trim();
        if (request.kind() == ReviewKind.GAP_CONFIRMED && (note == null || note.isEmpty())) {
            throw new IllegalArgumentException("Confirming a gap requires a note explaining why");
        }
        if (mapping.stale()) {
            throw new ConflictException("This mapping cites an outdated rule or course version; re-run matching before reviewing");
        }
        if (determinations.hasSigned(mapping.assessmentId(), mapping.jurisdictionId())) {
            throw new ConflictException("A signed determination covers this mapping; it must be reopened by a change before reviewing");
        }
        UUID actor = CurrentUser.require().id();
        UUID eventId = reviews.insert(TenantContext.require(), mappingId, request.kind(), note, actor);
        audit.record("REVIEW_RECORDED", "mapping", mappingId, Map.of(
                "reviewEventId", eventId.toString(), "kind", request.kind().name(), "note", note == null ? "" : note,
                "requirementRef", mapping.requirement().ref(), "jurisdiction", mapping.jurisdiction()));
        return mappings.findById(mappingId).orElseThrow();
    }
}
