package com.lexvay.determination.register;

import com.lexvay.determination.Outcome;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A program's target jurisdictions in the three lists of 34 CFR 668.43(a)(5)(v) and (c)(1) (F8, spec point 2). Every
 * target is in exactly one list. The lists report signed records only: a target without a current SIGNED
 * determination is in {@code noDeterminationMade} with reason NO_CURRENT_SIGNATURE, never inferred into another list.
 */
public record DeterminationLists(ProgramSummary program, OffsetDateTime generatedAt, List<Entry> determinedToMeet,
                                 List<Entry> determinedNotToMeet, List<Entry> noDeterminationMade) {

    public record ProgramSummary(UUID id, String name, String licenseType, String catalogYear) {
    }

    /**
     * A target jurisdiction. The determination fields are set when a current SIGNED determination exists; {@code reason}
     * is set only in {@code noDeterminationMade}; {@code reopenedDeterminationId} and {@code reopenedAt} only with
     * NO_CURRENT_SIGNATURE when the latest signature was reopened by a change.
     */
    public record Entry(String jurisdictionCode, String jurisdictionName, UUID determinationId, UUID signedBy,
                        String signedByName, OffsetDateTime signedAt, String ruleVersionLabel, String versionsHash,
                        Reason reason, UUID reopenedDeterminationId, OffsetDateTime reopenedAt) {
    }

    public enum Reason {
        /** A signer signed the outcome NOT_DETERMINED. */
        SIGNED_NO_DETERMINATION,
        /** No SIGNED determination exists for this target, including one that a change reopened. */
        NO_CURRENT_SIGNATURE
    }

    /**
     * One target jurisdiction as the repository reads it: the current SIGNED determination's outcome and fields, or
     * {@code outcome} null when there is none (then the latest reopened determination, if any).
     */
    public record Target(String jurisdictionCode, String jurisdictionName, Outcome outcome, UUID determinationId,
                         UUID signedBy, String signedByName, OffsetDateTime signedAt, String ruleVersionLabel,
                         String versionsHash, UUID reopenedDeterminationId, OffsetDateTime reopenedAt) {
    }

    /** Places each target in exactly one list by the outcome a person signed; nothing else decides the list. */
    public static DeterminationLists of(ProgramSummary program, OffsetDateTime generatedAt, List<Target> targets) {
        List<Entry> meet = new ArrayList<>();
        List<Entry> notMeet = new ArrayList<>();
        List<Entry> none = new ArrayList<>();
        for (Target t : targets) {
            if (t.outcome() == null) {
                none.add(new Entry(t.jurisdictionCode(), t.jurisdictionName(), null, null, null, null, null, null,
                        Reason.NO_CURRENT_SIGNATURE, t.reopenedDeterminationId(), t.reopenedAt()));
                continue;
            }
            switch (t.outcome()) {
                case MEETS -> meet.add(signed(t, null));
                case DOES_NOT_MEET -> notMeet.add(signed(t, null));
                case NOT_DETERMINED -> none.add(signed(t, Reason.SIGNED_NO_DETERMINATION));
            }
        }
        return new DeterminationLists(program, generatedAt, List.copyOf(meet), List.copyOf(notMeet), List.copyOf(none));
    }

    private static Entry signed(Target t, Reason reason) {
        return new Entry(t.jurisdictionCode(), t.jurisdictionName(), t.determinationId(), t.signedBy(), t.signedByName(),
                t.signedAt(), t.ruleVersionLabel(), t.versionsHash(), reason, null, null);
    }
}
