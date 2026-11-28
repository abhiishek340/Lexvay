package com.lexvay.rule.source;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One recorded check of a rule source, with the access status the source had when it was made. Only a changed check
 * (CHANGED, CHANGED_QUEUED) carries a document and a rule version: the draft it created, or the draft it waits behind.
 */
public record RuleSourceCheck(UUID id, OffsetDateTime checkedAt, CheckOutcome outcome, AccessStatus accessStatus,
                              Integer httpStatus, String contentHash, String detail, UUID documentId, UUID ruleVersionId) {
}
