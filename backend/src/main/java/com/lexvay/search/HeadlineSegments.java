package com.lexvay.search;

import static com.lexvay.assessment.match.PassageSearch.MATCH_END;
import static com.lexvay.assessment.match.PassageSearch.MATCH_START;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a database headline into segments by splitting on the two control characters the query put around
 * matches (D-152). The database never produces markup that reaches a client: the characters are removed and
 * everything else is passed through as plain text.
 */
final class HeadlineSegments {

    private HeadlineSegments() {
    }

    static List<Segment> parse(String headline) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        boolean inMatch = false;
        for (int i = 0; i < headline.length(); i++) {
            char c = headline.charAt(i);
            if (c == MATCH_START || c == MATCH_END) {
                flush(segments, run, inMatch);
                inMatch = c == MATCH_START;
            } else {
                run.append(c);
            }
        }
        flush(segments, run, inMatch);
        return segments;
    }

    private static void flush(List<Segment> segments, StringBuilder run, boolean match) {
        if (!run.isEmpty()) {
            segments.add(new Segment(run.toString(), match));
            run.setLength(0);
        }
    }
}
