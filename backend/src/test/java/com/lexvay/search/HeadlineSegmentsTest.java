package com.lexvay.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HeadlineSegmentsTest {

    private static final String S = "\u0001";
    private static final String E = "\u0002";

    @Test
    void splitsOnTheDelimitersAndTheSegmentsReconstructTheHeadline() {
        String headline = "Explain the " + S + "duty" + E + " to " + S + "warn" + E + " ... Week 4 " + S + "Duty" + E + " to warn.";
        List<Segment> segments = HeadlineSegments.parse(headline);
        assertThat(segments).containsExactly(
                new Segment("Explain the ", false), new Segment("duty", true), new Segment(" to ", false),
                new Segment("warn", true), new Segment(" ... Week 4 ", false), new Segment("Duty", true),
                new Segment(" to warn.", false));
        assertThat(segments.stream().map(Segment::text).reduce("", String::concat))
                .isEqualTo(headline.replace(S, "").replace(E, ""));
    }

    @Test
    void textWithoutMatchesIsOneUnmatchedSegment() {
        assertThat(HeadlineSegments.parse("plain text")).containsExactly(new Segment("plain text", false));
        assertThat(HeadlineSegments.parse("")).isEmpty();
    }

    @Test
    void markupInPageTextStaysPlainText() {
        assertThat(HeadlineSegments.parse("<b>" + S + "x" + E + "</b><script>alert(1)</script>")).containsExactly(
                new Segment("<b>", false), new Segment("x", true), new Segment("</b><script>alert(1)</script>", false));
    }

    @Test
    void unbalancedDelimitersNeverLeakIntoText() {
        assertThat(HeadlineSegments.parse(E + "a" + S + "b" + S + "c")).extracting(Segment::text).containsExactly("a", "b", "c");
    }
}
