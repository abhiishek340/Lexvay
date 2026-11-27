package com.lexvay.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pairs the requirements of two rule versions (D-122): first by identical ref, then, among the leftovers, a
 * removed with an added requirement whose texts are alike (Jaccard similarity of lower-cased word sets at or
 * above {@link #THRESHOLD}), best pairs first, so a renumbered requirement is not reported as removed and added.
 */
final class RequirementMatcher {

    static final double THRESHOLD = 0.80;

    private static final Pattern WORD = Pattern.compile("\\w+", Pattern.UNICODE_CHARACTER_CLASS);

    /** {@code before} is null for an added requirement, {@code after} for a removed one. */
    record Pair(Requirement before, Requirement after, boolean refChanged) {
    }

    private record Candidate(Requirement removed, Requirement added, double similarity, int removedIndex, int addedIndex) {
    }

    private RequirementMatcher() {
    }

    /** Pairs in the newer version's order, followed by the removed requirements in the older version's order. */
    static List<Pair> match(List<Requirement> older, List<Requirement> newer) {
        Map<String, Requirement> olderByRef = new HashMap<>();
        older.forEach(r -> olderByRef.put(r.ref(), r));
        Set<String> newerRefs = new HashSet<>();
        newer.forEach(r -> newerRefs.add(r.ref()));
        List<Requirement> removed = older.stream().filter(r -> !newerRefs.contains(r.ref())).toList();
        List<Requirement> added = newer.stream().filter(r -> !olderByRef.containsKey(r.ref())).toList();

        Map<UUID, Requirement> renumberedFrom = renumbered(removed, added);
        Set<UUID> renumberedIds = new HashSet<>();
        renumberedFrom.values().forEach(r -> renumberedIds.add(r.id()));

        List<Pair> pairs = new ArrayList<>();
        for (Requirement after : newer) {
            Requirement sameRef = olderByRef.get(after.ref());
            Requirement renumbered = renumberedFrom.get(after.id());
            pairs.add(sameRef != null ? new Pair(sameRef, after, false)
                    : renumbered != null ? new Pair(renumbered, after, true) : new Pair(null, after, false));
        }
        removed.stream().filter(r -> !renumberedIds.contains(r.id())).forEach(r -> pairs.add(new Pair(r, null, false)));
        return pairs;
    }

    /** Greedy pairing by descending similarity; returns the removed requirement for each added requirement id. */
    private static Map<UUID, Requirement> renumbered(List<Requirement> removed, List<Requirement> added) {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < removed.size(); i++) {
            Set<String> removedWords = words(removed.get(i).text());
            for (int j = 0; j < added.size(); j++) {
                double similarity = jaccard(removedWords, words(added.get(j).text()));
                if (similarity >= THRESHOLD) {
                    candidates.add(new Candidate(removed.get(i), added.get(j), similarity, i, j));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::similarity).reversed()
                .thenComparingInt(Candidate::removedIndex).thenComparingInt(Candidate::addedIndex));
        Map<UUID, Requirement> renumberedFrom = new HashMap<>();
        Set<UUID> taken = new HashSet<>();
        for (Candidate c : candidates) {
            if (!taken.contains(c.removed().id()) && !renumberedFrom.containsKey(c.added().id())) {
                taken.add(c.removed().id());
                renumberedFrom.put(c.added().id(), c.removed());
            }
        }
        return renumberedFrom;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / union.size();
    }

    static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }
}
