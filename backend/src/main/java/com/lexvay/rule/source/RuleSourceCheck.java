package com.lexvay.rule.source;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One recorded check of a rule source. Only a CHANGED check carries a document and a rule version. */
public record RuleSourceCheck(UUID id, OffsetDateTime checkedAt, CheckOutcome outcome, Integer httpStatus,
                              String contentHash, String detail, UUID documentId, UUID ruleVersionId) {
}
