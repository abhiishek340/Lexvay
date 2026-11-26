package com.lexvay.document;

import java.util.Optional;

/**
 * Product rule 2. A quote counts as evidence only if, after collapsing runs of whitespace in both the
 * quote and the page text, it is an exact substring of the page. On success the offsets refer to the
 * original page text so the span can be shown exactly as stored. No fuzzy matching of any kind.
 */
public final class QuoteVerifier {

    public record Match(int charStart, int charEnd, String exactText) {
    }

    private record Normalized(String text, int[] originalIndex) {
    }

    private QuoteVerifier() {
    }

    public static Optional<Match> find(String pageText, String quote) {
        if (pageText == null || quote == null) {
            return Optional.empty();
        }
        Normalized page = normalize(pageText);
        String needle = normalize(quote).text();
        if (needle.isEmpty()) {
            return Optional.empty();
        }
        int index = page.text().indexOf(needle);
        if (index < 0) {
            return Optional.empty();
        }
        int start = page.originalIndex()[index];
        int end = page.originalIndex()[index + needle.length() - 1] + 1;
        return Optional.of(new Match(start, end, pageText.substring(start, end)));
    }

    public static String normalizeWhitespace(String text) {
        return text == null ? "" : normalize(text).text();
    }

    private static Normalized normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int[] map = new int[text.length()];
        boolean pendingSpace = false;
        int pendingSpaceIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!pendingSpace) {
                    pendingSpace = true;
                    pendingSpaceIndex = i;
                }
                continue;
            }
            if (pendingSpace && out.length() > 0) {
                map[out.length()] = pendingSpaceIndex;
                out.append(' ');
            }
            pendingSpace = false;
            map[out.length()] = i;
            out.append(c);
        }
        int[] trimmed = new int[out.length()];
        System.arraycopy(map, 0, trimmed, 0, out.length());
        return new Normalized(out.toString(), trimmed);
    }
}
