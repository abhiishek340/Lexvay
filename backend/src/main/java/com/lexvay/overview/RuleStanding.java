package com.lexvay.overview;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Which rule a program's jurisdiction is assessed against today. {@code inForce} is null when none is, and then
 * {@code noRuleInForce} says so with the date: the assessment does not run for that state (D-556). {@code upcoming}
 * is the next final version that takes effect later, with {@code upcomingNotice} ("Upcoming from <date>").
 * {@code sourceNotice} is "Source stale" when any rule source of the state is not HEALTHY: the monitor has not read the
 * board's page recently or cannot, which says the rules may be out of date and not that they changed (RG-2, D-573).
 */
public record RuleStanding(String jurisdiction, RuleRef inForce, RuleRef upcoming, String noRuleInForce, String upcomingNotice,
                           String sourceNotice) {

    /** The notice a state shows while a source of it is not HEALTHY. */
    public static final String SOURCE_STALE = "Source stale";

    public record RuleRef(UUID id, String label, LocalDate effectiveFrom) {
    }
}
