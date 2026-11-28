package com.lexvay.rule;

import java.time.LocalDate;

/**
 * The legal status and dates of a rule version, with the citation that supports the dates. A date without a basis
 * is refused, and a rule cannot end before it begins: the same two rules the database enforces (V021).
 */
public record LegalPosition(LegalStatus status, LocalDate effectiveFrom, LocalDate effectiveTo, LocalDate publishedAt,
                            LocalDate adoptedAt, String datesBasis) {

    public LegalPosition {
        if (status == null) {
            throw new IllegalArgumentException("A legal status is required");
        }
        datesBasis = datesBasis == null || datesBasis.isBlank() ? null : datesBasis.trim();
        if (datesBasis == null && (effectiveFrom != null || effectiveTo != null || publishedAt != null || adoptedAt != null)) {
            throw new IllegalArgumentException("Say where the dates come from: a date needs its basis");
        }
        if (effectiveFrom != null && effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("The end date must be after the effective date");
        }
    }

    /** What is known of a text that was only observed: no law is attached to it yet. */
    public static LegalPosition unknown() {
        return new LegalPosition(LegalStatus.UNKNOWN, null, null, null, null, null);
    }
}
