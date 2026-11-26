package com.lexvay.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.TextDiff.Op;
import com.lexvay.common.TextDiff.Segment;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TextDiffTest {

    private static String rebuild(List<Segment> segments, Op... ops) {
        List<Op> wanted = List.of(ops);
        return segments.stream().filter(s -> wanted.contains(s.op())).map(Segment::text).collect(Collectors.joining());
    }

    private static void assertReconstructs(List<Segment> segments, String a, String b) {
        assertThat(rebuild(segments, Op.EQUAL, Op.DELETE)).as("EQUAL + DELETE").isEqualTo(a);
        assertThat(rebuild(segments, Op.EQUAL, Op.INSERT)).as("EQUAL + INSERT").isEqualTo(b);
        for (int i = 1; i < segments.size(); i++) {
            assertThat(segments.get(i).op()).as("adjacent segments are merged").isNotEqualTo(segments.get(i - 1).op());
        }
        assertThat(segments).allSatisfy(s -> assertThat(s.text()).isNotEmpty());
    }

    @Test
    void identicalTextIsOneEqualSegment() {
        assertThat(TextDiff.words("at least 600 hours", "at least 600 hours"))
                .containsExactly(new Segment(Op.EQUAL, "at least 600 hours"));
    }

    @Test
    void aReplacedWordIsDeleteThenInsertBetweenEqualRuns() {
        assertThat(TextDiff.words("at least 600 clock hours", "at least 700 clock hours")).containsExactly(
                new Segment(Op.EQUAL, "at least "), new Segment(Op.DELETE, "600"), new Segment(Op.INSERT, "700"),
                new Segment(Op.EQUAL, " clock hours"));
    }

    @Test
    void insertionsAndDeletionsAreReported() {
        assertThat(TextDiff.words("coursework in ethics", "coursework in professional ethics")).containsExactly(
                new Segment(Op.EQUAL, "coursework in "), new Segment(Op.INSERT, "professional "), new Segment(Op.EQUAL, "ethics"));
        assertThat(TextDiff.words("coursework in professional ethics", "coursework in ethics")).containsExactly(
                new Segment(Op.EQUAL, "coursework in "), new Segment(Op.DELETE, "professional "), new Segment(Op.EQUAL, "ethics"));
    }

    @Test
    void emptyInputs() {
        assertThat(TextDiff.words("", "")).isEmpty();
        assertThat(TextDiff.words("abc", "")).containsExactly(new Segment(Op.DELETE, "abc"));
        assertThat(TextDiff.words("", "abc")).containsExactly(new Segment(Op.INSERT, "abc"));
        assertThat(TextDiff.lines("", "")).isEmpty();
        assertThat(TextDiff.lines("one\n", "")).containsExactly(new Segment(Op.DELETE, "one\n"));
        assertThat(TextDiff.lines("", "one\n")).containsExactly(new Segment(Op.INSERT, "one\n"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            at least 600 clock hours of supervised internship | at least 700 clock hours of supervised internship
            a b c | c b a
            one two three | one two three four five
            ethics, law; and  practice | ethics law and practice
            § 12.4(b): 60 semester hours | § 12.4(b): 60 semester credit hours
            café résumé | cafe resume
            x | y
            """)
    void wordSegmentsReconstructBothInputs(String a, String b) {
        assertReconstructs(TextDiff.words(a, b), a, b);
        assertReconstructs(TextDiff.words(b, a), b, a);
    }

    @Test
    void wordSegmentsReconstructMultilineAndWhitespaceOnlyInputs() {
        String a = "The applicant shall have completed a graduate degree.\n\nThe degree shall include ethics.\n";
        String b = "The applicant must have completed a master's degree.\r\nThe degree shall include ethics and law.";
        assertReconstructs(TextDiff.words(a, b), a, b);
        assertReconstructs(TextDiff.words("   ", "\n\t"), "   ", "\n\t");
        assertReconstructs(TextDiff.words("", "   "), "", "   ");
    }

    @Test
    void lineDiffKeepsEqualLinesAndRefinesAReplacedBlockToWords() {
        String a = "Rule 1\nAt least 600 hours.\nRule 3\n";
        String b = "Rule 1\nAt least 700 hours.\nRule 3\n";
        assertThat(TextDiff.lines(a, b)).containsExactly(
                new Segment(Op.EQUAL, "Rule 1\nAt least "), new Segment(Op.DELETE, "600"), new Segment(Op.INSERT, "700"),
                new Segment(Op.EQUAL, " hours.\nRule 3\n"));
    }

    @Test
    void lineDiffReportsAddedAndRemovedLines() {
        assertThat(TextDiff.lines("a\nb\n", "a\nnew line\nb\n")).containsExactly(
                new Segment(Op.EQUAL, "a\n"), new Segment(Op.INSERT, "new line\n"), new Segment(Op.EQUAL, "b\n"));
        assertThat(TextDiff.lines("a\nold line\nb\n", "a\nb\n")).containsExactly(
                new Segment(Op.EQUAL, "a\n"), new Segment(Op.DELETE, "old line\n"), new Segment(Op.EQUAL, "b\n"));
    }

    @Test
    void lineSegmentsReconstructBothInputs() {
        String[][] cases = {
                {"", "x"}, {"x", ""}, {"no trailing newline", "no trailing newline\n"}, {"a\nb\nc", "a\nB\nc\nd"},
                {"line one\r\nline two\r\n", "line one\r\nline 2\r\nline three\r\n"},
                {"\n\n", "\n"}, {"alpha beta\ngamma\n", "gamma\nalpha beta\n"},
                {"The applicant shall have completed 600 hours.\nEthics.\n", "Ethics.\nThe applicant shall complete 700 hours.\nNew rule.\n"}};
        for (String[] c : cases) {
            assertReconstructs(TextDiff.lines(c[0], c[1]), c[0], c[1]);
            assertReconstructs(TextDiff.lines(c[1], c[0]), c[1], c[0]);
        }
    }
}
