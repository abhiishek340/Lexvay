package com.lexvay.workqueue;

/** What needs a person, in the order the queue lists it. Who sees which kind is decided in {@link WorkQueueRepository}. */
public enum WorkQueueKind {
    REQUIREMENTS_TO_REVIEW,
    EVIDENCE_REQUESTS_OPEN,
    RULE_VERSIONS_TO_APPROVE,
    EXTRACTION_FAILED,
    CHANGES_OPEN,
    READY_TO_SIGN,
    ASSESSMENTS_FAILED
}
