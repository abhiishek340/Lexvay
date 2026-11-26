package com.lexvay.assessment.match;

import com.lexvay.assessment.Finding;
import java.util.UUID;

/** The model's validated proposal, before quote verification. */
public record MatchProposal(Finding finding, UUID documentId, Integer pageNumber, String quote, String rationale) {
}
