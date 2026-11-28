package com.lexvay.rule.source;

import java.util.UUID;

/**
 * What a check found, before it is recorded. {@code validators} are the ones to keep for the next conditional request,
 * or null to keep the source's current ones.
 */
public record CheckFinding(CheckOutcome outcome, Integer httpStatus, String contentHash, String detail, UUID documentId,
                           UUID ruleVersionId, Validators validators) {

    static final int MAX_DETAIL_CHARS = 1000;

    static CheckFinding failed(Integer httpStatus, String message) {
        return without(CheckOutcome.FETCH_FAILED, httpStatus, text(message, "The page could not be read"));
    }

    static CheckFinding suspect(Integer httpStatus, String reason) {
        return without(CheckOutcome.SUSPECT_CONTENT, httpStatus, text(reason, "The content is not the rule text"));
    }

    static CheckFinding blockedByRobots(String reason) {
        return without(CheckOutcome.BLOCKED_BY_ROBOTS, null, text(reason, "robots.txt forbids the page"));
    }

    static CheckFinding notModified(int httpStatus) {
        return without(CheckOutcome.NOT_MODIFIED, httpStatus, null);
    }

    private static CheckFinding without(CheckOutcome outcome, Integer httpStatus, String detail) {
        return new CheckFinding(outcome, httpStatus, null, detail, null, null, null);
    }

    private static String text(String message, String fallback) {
        String text = message == null || message.isBlank() ? fallback : message;
        return text.length() <= MAX_DETAIL_CHARS ? text : text.substring(0, MAX_DETAIL_CHARS);
    }
}
