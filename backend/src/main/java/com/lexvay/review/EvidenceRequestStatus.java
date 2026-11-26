package com.lexvay.review;

/**
 * OPEN: the mapping's current decision is EVIDENCE_REQUESTED. ANSWERED: the latest request has a response and
 * no decision has been made since. ALL is a filter only; a row is always OPEN or ANSWERED.
 */
public enum EvidenceRequestStatus {
    OPEN, ANSWERED, ALL
}
