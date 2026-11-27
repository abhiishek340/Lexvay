package com.lexvay.rule;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The search terms of a requirement (F7c): words or two-word phrases a syllabus would use for its subject, which
 * retrieval searches together with the requirement's own words. Terms come from the model or from a reviewer; this
 * class is the one place that decides which of them are kept.
 */
public final class SearchTerms {

    /** What a person may store, and what the database constraint allows. */
    public static final int MAX_STORED = 12;
    /** What the model is asked for, and what the extraction schema allows. */
    public static final int MAX_PROPOSED = 8;
    public static final int MAX_LENGTH = 40;

    /** The states, so that a term never narrows a search to one jurisdiction. Matched as whole words. */
    private static final List<Pattern> STATES = List.of("alabama", "alaska", "arizona", "arkansas", "california", "colorado",
            "connecticut", "delaware", "florida", "georgia", "hawaii", "idaho", "illinois", "indiana", "iowa", "kansas",
            "kentucky", "louisiana", "maine", "maryland", "massachusetts", "michigan", "minnesota", "mississippi",
            "missouri", "montana", "nebraska", "nevada", "new hampshire", "new jersey", "new mexico", "new york",
            "north carolina", "north dakota", "ohio", "oklahoma", "oregon", "pennsylvania", "rhode island",
            "south carolina", "south dakota", "tennessee", "texas", "utah", "vermont", "virginia", "washington",
            "west virginia", "wisconsin", "wyoming", "district of columbia").stream()
            .map(state -> Pattern.compile("(?<![\\p{L}\\p{N}])" + state + "(?![\\p{L}\\p{N}])")).toList();

    private SearchTerms() {
    }

    /**
     * The terms to store: trimmed, inner whitespace collapsed, lower-cased, without duplicates and in their given
     * order. Dropped are empty terms, terms containing a digit or a state name, and terms whose every word is already
     * a word of the requirement's title or text (searching for them adds nothing). Whole words are compared; the
     * database's stemming is not available here.
     */
    public static List<String> normalise(List<String> proposed, String title, String text) {
        Set<String> known = new HashSet<>(RequirementMatcher.words(title));
        known.addAll(RequirementMatcher.words(text));
        Set<String> kept = new LinkedHashSet<>();
        for (String term : proposed) {
            String clean = term.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (!clean.isEmpty() && clean.chars().noneMatch(Character::isDigit) && !namesState(clean)
                    && !known.containsAll(RequirementMatcher.words(clean))) {
                kept.add(clean);
            }
        }
        return List.copyOf(kept);
    }

    private static boolean namesState(String term) {
        return STATES.stream().anyMatch(state -> state.matcher(term).find());
    }
}
