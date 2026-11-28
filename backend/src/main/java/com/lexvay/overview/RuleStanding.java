package com.lexvay.overview;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Which rule a program's jurisdiction is assessed against today. {@code inForce} is null when none is, and then
 * {@code noRuleInForce} says so with the date: the assessment does not run for that state (D-556). {@code upcoming}
 * is the next final version that takes effect later, with {@code upcomingNotice} ("Upcoming from <date>").
 */
public record RuleStanding(String jurisdiction, RuleRef inForce, RuleRef upcoming, String noRuleInForce, String upcomingNotice) {

    public record RuleRef(UUID id, String label, LocalDate effectiveFrom) {
    }
}
