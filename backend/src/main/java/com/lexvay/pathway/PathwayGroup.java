package com.lexvay.pathway;

import com.lexvay.program.CreditUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One group of a program's graduation paths for a catalog year, as it is today: its members carry the credits of the
 * course version in force. {@code minCount} and {@code minCredits} are null when the minimum is not recorded, which
 * {@link PathwayCoverage} treats as unknown. {@code citation} is the verified passage that states the group, if
 * a person gave one.
 */
public record PathwayGroup(UUID id, String label, GroupKind kind, Integer minCount, BigDecimal minCredits,
                           List<Member> members, Citation citation) {

    /** A course of the group. {@code credits} is null when unknown. */
    public record Member(UUID courseId, String code, String title, BigDecimal credits, CreditUnit creditUnit) {
    }

    public record Citation(UUID spanId, UUID documentId, String filename, int pageNumber, String quote) {
    }
}
