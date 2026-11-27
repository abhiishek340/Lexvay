package com.lexvay.assessment.match;

import com.lexvay.document.QuoteVerifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What the model sees of a candidate page (D-174). A page of at most {@link #WHOLE_PAGE_CHARS} characters is sent
 * whole. A longer one is sent as its header (the first lines, up to {@link #HEADER_CHARS} characters, which name
 * the course) plus the fragments the database chose with ts_headline for the same lexemes that ranked it. Every
 * fragment is located in the stored page with {@link QuoteVerifier#find}, widened to whole lines, merged with
 * what it touches and emitted in page order, parts separated by a line holding only {@link #OMISSION}. If a
 * fragment cannot be located the first {@link #WHOLE_PAGE_CHARS} characters are sent instead. Every part is a
 * verbatim slice of the page, so a quote copied from within one part verifies against the full stored page.
 */
public final class PassageExcerpter {

    public static final int WHOLE_PAGE_CHARS = 2500;
    public static final String OMISSION = "[...]";

    /** Separates the fragments in a headline. A control character cannot occur in page text. */
    public static final String FRAGMENT_DELIMITER = "\u0003";

    /** ts_headline options. An empty selector has to be written as a pair of double quotes. */
    public static final String HEADLINE_OPTIONS = "MaxFragments=3, MaxWords=120, MinWords=60, FragmentDelimiter="
            + FRAGMENT_DELIMITER + ", StartSel=\"\", StopSel=\"\"";

    static final int HEADER_CHARS = 300;

    /** A half-open range of the page that always starts and ends on a line boundary. */
    private record Part(int start, int end) {
    }

    private PassageExcerpter() {
    }

    /** @param headline the database's ts_headline of {@code page}; null or blank when the page was not long enough to need one */
    public static String excerpt(String page, String headline) {
        if (page.length() <= WHOLE_PAGE_CHARS) {
            return page;
        }
        Optional<List<Part>> fragments = locate(page, headline);
        if (fragments.isEmpty()) {
            return page.substring(0, WHOLE_PAGE_CHARS);
        }
        List<Part> parts = new ArrayList<>(fragments.get());
        parts.add(new Part(0, headerEnd(page)));
        String excerpt = merge(parts).stream().map(p -> page.substring(p.start(), p.end()))
                .collect(Collectors.joining("\n" + OMISSION + "\n"));
        return excerpt.length() < page.length() ? excerpt : page;
    }

    private static Optional<List<Part>> locate(String page, String headline) {
        if (headline == null || headline.isBlank()) {
            return Optional.empty();
        }
        List<Part> located = new ArrayList<>();
        for (String fragment : headline.split(FRAGMENT_DELIMITER)) {
            if (fragment.isBlank()) {
                continue;
            }
            Optional<QuoteVerifier.Match> match = QuoteVerifier.find(page, fragment);
            if (match.isEmpty()) {
                return Optional.empty();
            }
            located.add(new Part(page.lastIndexOf('\n', match.get().charStart() - 1) + 1, lineEnd(page, match.get().charEnd())));
        }
        return located.isEmpty() ? Optional.empty() : Optional.of(located);
    }

    /** The end of the last whole line that fits the header budget; a first line longer than the budget is cut at it. */
    private static int headerEnd(String page) {
        int lastLineBreak = page.lastIndexOf('\n', HEADER_CHARS);
        return lastLineBreak > 0 ? lastLineBreak : HEADER_CHARS;
    }

    private static int lineEnd(String page, int from) {
        int lineBreak = page.indexOf('\n', from);
        return lineBreak < 0 ? page.length() : lineBreak;
    }

    /** Sorts by position and joins parts that overlap or are separated by nothing but a line break. */
    private static List<Part> merge(List<Part> parts) {
        List<Part> merged = new ArrayList<>();
        for (Part part : parts.stream().sorted(Comparator.comparingInt(Part::start)).toList()) {
            Part last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && part.start() <= last.end() + 1) {
                merged.set(merged.size() - 1, new Part(last.start(), Math.max(last.end(), part.end())));
            } else {
                merged.add(part);
            }
        }
        return merged;
    }
}
