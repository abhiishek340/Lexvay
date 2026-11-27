package com.lexvay.eval;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How close the search terms a real model proposed are to the hand-written ones in the golden set, per requirement
 * and on average. The measure is the Jaccard index of the two sets of terms (shared terms over all distinct terms), so
 * 100 % is the same set and 0 % shares nothing. Terms are compared as stored, which is after
 * {@link com.lexvay.rule.SearchTerms#normalise}; the golden terms are checked to be stable under it. Informational:
 * the evaluation has no limit on it, because the terms only widen retrieval and never produce evidence.
 */
public record TermOverlap(List<Entry> entries) {

    /** One requirement: the golden terms, the model's terms and their Jaccard index (a missing extraction proposes none). */
    public record Entry(String subject, List<String> golden, List<String> proposed, double jaccard) {
    }

    /** @param proposed the model's stored terms per golden key, before the golden list replaced its extraction */
    public static TermOverlap measure(Golden golden, Map<String, List<String>> proposed) {
        return new TermOverlap(golden.requirements().stream().map(e -> {
            List<String> mine = proposed.getOrDefault(e.key(), List.of());
            return new Entry(e.key(), e.searchTerms(), mine, jaccard(e.searchTerms(), mine));
        }).toList());
    }

    /** Both sets empty is no disagreement at all, so 1; the golden file never has an empty list. */
    static double jaccard(List<String> a, List<String> b) {
        Set<String> shared = new LinkedHashSet<>(a);
        shared.retainAll(b);
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all.isEmpty() ? 1.0 : (double) shared.size() / all.size();
    }

    public double mean() {
        return entries.stream().mapToDouble(Entry::jaccard).average().orElse(0.0);
    }
}
