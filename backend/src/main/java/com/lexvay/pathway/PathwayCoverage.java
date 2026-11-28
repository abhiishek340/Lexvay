package com.lexvay.pathway;

import com.lexvay.program.CreditUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Answers, for the active groups of one program and catalog year, whether a set of courses lies on every valid
 * graduation path and on some valid path, and what the shortest path costs in credits. A path is a choice of courses
 * that satisfies every group; groups are independent, and a course may belong to several of them.
 *
 * <p>The evaluator reasons about each group on its own and never enumerates paths. Course set {@code S} lies on every path
 * when every path contains at least one course of it. A path that avoids {@code S} exists exactly when every group can be
 * satisfied by its members outside {@code S}, so one group that cannot decides: an ALL_OF group that has a course of
 * {@code S}; a CHOOSE_COUNT group whose other members number fewer than its minimum; a CHOOSE_CREDITS group whose other
 * members add up to fewer credits than its minimum.
 *
 * <p>Whatever is not recorded is {@link Tri#UNKNOWN}, never TRUE: a minimum, a member's credits, a course that no group
 * places, credits in two units (there is no conversion, D-587). The reasons come back with the answer so that a person
 * can see what to record. The class is pure: no database, no clock, no model (product rules 3 and 4).
 */
public final class PathwayCoverage {

    /** A course of the program, as the evidence names it. */
    public record Course(UUID id, String code) {
    }

    /** {@code unknowns} says what is not recorded; it is empty unless {@code value} is UNKNOWN. */
    public record Verdict(Tri value, List<String> unknowns) {
    }

    /**
     * The fewest credits a graduate takes, or null when that cannot be said. {@code basis} names the groups the number
     * comes from; {@code unknowns} is empty when the minimum is known.
     */
    public record Minimum(BigDecimal credits, CreditUnit unit, String basis, List<String> unknowns) {

        public boolean known() {
            return credits != null;
        }
    }

    static final String NO_GROUPS = "no graduation groups are recorded for this catalog year";

    /** A question about one group; {@code reason} is set when the answer is UNKNOWN. */
    private record Answer(Tri value, String reason) {

        static final Answer TRUE = new Answer(Tri.TRUE, null);
        static final Answer FALSE = new Answer(Tri.FALSE, null);

        static Answer unknown(String reason) {
            return new Answer(Tri.UNKNOWN, reason);
        }
    }

    private final List<PathwayGroup> groups;

    public PathwayCoverage(List<PathwayGroup> groups) {
        this.groups = List.copyOf(groups);
    }

    /** TRUE when every valid path contains at least one of the courses; FALSE when some valid path contains none of them. */
    public Verdict onEveryPath(Collection<Course> courses) {
        if (courses.isEmpty()) {
            return new Verdict(Tri.FALSE, List.of());
        }
        Set<UUID> chosen = ids(courses);
        Tri result = Tri.FALSE;
        Set<String> unknowns = new LinkedHashSet<>();
        for (PathwayGroup group : groups) {
            Answer answer = cannotAvoid(group, chosen);
            if (answer.value() == Tri.TRUE) {
                return new Verdict(Tri.TRUE, List.of());
            }
            if (answer.value() == Tri.UNKNOWN) {
                result = Tri.UNKNOWN;
                unknowns.add(answer.reason());
            }
        }
        List<String> unplaced = unplaced(courses);
        if (!unplaced.isEmpty()) {
            result = Tri.UNKNOWN;
            unknowns.addAll(unplaced);
        }
        return new Verdict(result, List.copyOf(unknowns));
    }

    /** TRUE when a valid path contains at least one of the courses; FALSE when no path does, or no path exists. */
    public Verdict onSomePath(Collection<Course> courses) {
        if (courses.isEmpty()) {
            return new Verdict(Tri.FALSE, List.of());
        }
        Set<UUID> chosen = ids(courses);
        boolean placed = groups.stream().anyMatch(g -> g.members().stream().anyMatch(m -> chosen.contains(m.courseId())));
        if (!placed) {
            return new Verdict(Tri.UNKNOWN, unplaced(courses));
        }
        Tri result = Tri.TRUE;
        Set<String> unknowns = new LinkedHashSet<>();
        for (PathwayGroup group : groups) {
            Answer answer = satisfiable(group);
            result = result.and(answer.value());
            if (answer.value() == Tri.UNKNOWN) {
                unknowns.add(answer.reason());
            }
        }
        return new Verdict(result, result == Tri.UNKNOWN ? List.copyOf(unknowns) : List.of());
    }

    /**
     * The credits of the cheapest path: every member of an ALL_OF group plus the cheapest selection that satisfies each
     * CHOOSE group. Known only when every credit that could matter is recorded, in one unit, and no course is counted by
     * two groups (a course taken once is one set of credits; telling which group it counts for would mean enumerating).
     */
    public Minimum minimumCredits() {
        List<String> unknowns = new ArrayList<>();
        List<PathwayGroup.Member> all = groups.stream().flatMap(g -> g.members().stream()).toList();
        if (groups.isEmpty()) {
            unknowns.add(NO_GROUPS);
        } else if (all.isEmpty()) {
            unknowns.add("no courses are placed in the graduation groups");
        }
        Set<CreditUnit> units = unitsOf(all);
        if (units.size() > 1) {
            unknowns.add("credits are recorded in more than one unit (" + units.stream().map(Enum::name).sorted().collect(Collectors.joining(", ")) + ")");
        }
        sharedCodes(all).forEach(code -> unknowns.add(code + " is in more than one group, so its credits cannot be counted once"));
        long total = 0;
        for (PathwayGroup group : groups) {
            Long cheapest = cheapest(group, unknowns);
            total += cheapest == null ? 0 : cheapest;
        }
        String basis = "the credits of the courses in " + groups.stream().map(PathwayGroup::label).collect(Collectors.joining("; "));
        if (!unknowns.isEmpty()) {
            return new Minimum(null, null, basis, List.copyOf(new LinkedHashSet<>(unknowns)));
        }
        return new Minimum(BigDecimal.valueOf(total, 2), units.iterator().next(), basis, List.of());
    }

    /** Whether every valid path must contain a course of {@code chosen} because of this one group. */
    private static Answer cannotAvoid(PathwayGroup group, Set<UUID> chosen) {
        if (group.members().stream().noneMatch(m -> chosen.contains(m.courseId()))) {
            return Answer.FALSE;
        }
        if (minimumMissing(group)) {
            return notRecorded(group);
        }
        List<PathwayGroup.Member> others = group.members().stream().filter(m -> !chosen.contains(m.courseId())).toList();
        return switch (group.kind()) {
            case ALL_OF -> Answer.TRUE;
            case CHOOSE_COUNT -> others.size() < group.minCount() ? Answer.TRUE : Answer.FALSE;
            case CHOOSE_CREDITS -> creditsOf(group, others, false);
        };
    }

    /** Whether the group can be satisfied at all, which every valid path needs. */
    private static Answer satisfiable(PathwayGroup group) {
        if (minimumMissing(group)) {
            return notRecorded(group);
        }
        return switch (group.kind()) {
            case ALL_OF -> Answer.TRUE;
            case CHOOSE_COUNT -> group.members().size() >= group.minCount() ? Answer.TRUE : Answer.FALSE;
            case CHOOSE_CREDITS -> creditsOf(group, group.members(), true);
        };
    }

    /**
     * Compares the credits of {@code members} with the group's minimum: TRUE when they reach it if {@code reaches}, and
     * TRUE when they fall short of it otherwise. Credits that are not recorded can only add, so a known sum that reaches
     * the minimum settles the question; otherwise a member without credits leaves it open.
     */
    private static Answer creditsOf(PathwayGroup group, List<PathwayGroup.Member> members, boolean reaches) {
        if (unitsOf(members).size() > 1) {
            return Answer.unknown(group.label() + ": credits are recorded in more than one unit");
        }
        BigDecimal known = members.stream().map(PathwayGroup.Member::credits).filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (known.compareTo(group.minCredits()) >= 0) {
            return reaches ? Answer.TRUE : Answer.FALSE;
        }
        List<String> missing = missingCredits(members);
        if (!missing.isEmpty()) {
            return Answer.unknown(group.label() + ": credits not recorded for " + String.join(", ", missing));
        }
        return reaches ? Answer.FALSE : Answer.TRUE;
    }

    private static boolean minimumMissing(PathwayGroup group) {
        return switch (group.kind()) {
            case ALL_OF -> false;
            case CHOOSE_COUNT -> group.minCount() == null;
            case CHOOSE_CREDITS -> group.minCredits() == null;
        };
    }

    private static Answer notRecorded(PathwayGroup group) {
        return Answer.unknown(group.label() + ": minimum not recorded");
    }

    private static List<String> missingCredits(List<PathwayGroup.Member> members) {
        return members.stream().filter(m -> m.credits() == null).map(PathwayGroup.Member::code).toList();
    }

    /** The units in which the members that have credits record them. */
    private static Set<CreditUnit> unitsOf(List<PathwayGroup.Member> members) {
        return members.stream().filter(m -> m.credits() != null).map(PathwayGroup.Member::creditUnit)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CreditUnit.class)));
    }

    /** The credits, in hundredths, of the cheapest selection of one group; null, with the reasons added, when that cannot be said. */
    private static Long cheapest(PathwayGroup group, List<String> unknowns) {
        boolean complete = true;
        if (minimumMissing(group)) {
            unknowns.add(notRecorded(group).reason());
            complete = false;
        }
        List<String> missing = missingCredits(group.members());
        if (!missing.isEmpty()) {
            unknowns.add(group.label() + ": credits not recorded for " + String.join(", ", missing));
            complete = false;
        }
        if (!complete) {
            return null;
        }
        List<Integer> hundredths = group.members().stream().map(m -> m.credits().movePointRight(2).intValueExact()).sorted().toList();
        return switch (group.kind()) {
            case ALL_OF -> sum(hundredths);
            case CHOOSE_COUNT -> cheapestCount(group, hundredths, unknowns);
            case CHOOSE_CREDITS -> cheapestReaching(group, hundredths, unknowns);
        };
    }

    private static long sum(List<Integer> hundredths) {
        return hundredths.stream().mapToLong(Integer::longValue).sum();
    }

    /** The smallest {@code minCount} credits of the group, given ascending. */
    private static Long cheapestCount(PathwayGroup group, List<Integer> hundredths, List<String> unknowns) {
        if (hundredths.size() < group.minCount()) {
            unknowns.add(group.label() + ": it asks for " + group.minCount() + " of " + hundredths.size() + " courses");
            return null;
        }
        return sum(hundredths.subList(0, group.minCount()));
    }

    /** The smallest total of a selection of members that reaches the group's minimum credits: a subset sum over hundredths. */
    private static Long cheapestReaching(PathwayGroup group, List<Integer> hundredths, List<String> unknowns) {
        int target = group.minCredits().movePointRight(2).intValueExact();
        int all = (int) sum(hundredths);
        if (all < target) {
            unknowns.add(group.label() + ": its courses add up to fewer credits than it asks for");
            return null;
        }
        BitSet reachable = new BitSet(all + 1);
        reachable.set(0);
        for (int credit : hundredths) {
            for (int total = reachable.previousSetBit(all - credit); total >= 0; total = reachable.previousSetBit(total - 1)) {
                reachable.set(total + credit);
            }
        }
        return (long) reachable.nextSetBit(target);
    }

    /** The codes of the courses that two groups both hold. */
    private static List<String> sharedCodes(List<PathwayGroup.Member> members) {
        Map<UUID, Integer> counts = new HashMap<>();
        members.forEach(m -> counts.merge(m.courseId(), 1, Integer::sum));
        return members.stream().filter(m -> counts.get(m.courseId()) > 1).map(PathwayGroup.Member::code).distinct().sorted().toList();
    }

    private List<String> unplaced(Collection<Course> courses) {
        if (groups.isEmpty()) {
            return List.of(NO_GROUPS);
        }
        Set<UUID> placed = groups.stream().flatMap(g -> g.members().stream()).map(PathwayGroup.Member::courseId).collect(Collectors.toSet());
        return courses.stream().filter(c -> !placed.contains(c.id())).map(c -> c.code() + " is not placed in a graduation group")
                .distinct().toList();
    }

    private static Set<UUID> ids(Collection<Course> courses) {
        return courses.stream().map(Course::id).collect(Collectors.toSet());
    }
}
