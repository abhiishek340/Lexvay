package com.lexvay.common;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one text diff utility (D-121): Myers diff from java-diff-utils over word or line tokens, returned as merged
 * segments. Concatenating the EQUAL and DELETE segments reproduces the first text; EQUAL and INSERT the second.
 */
public final class TextDiff {

    public enum Op {
        EQUAL, DELETE, INSERT
    }

    public record Segment(Op op, String text) {
    }

    /** Word and non-word runs; joining the tokens reproduces the input exactly. */
    private static final Pattern WORDS = Pattern.compile("\\w+|\\W+", Pattern.UNICODE_CHARACTER_CLASS);

    /** Zero-width split after each line feed, so every line keeps its terminator. */
    private static final Pattern AFTER_LINE_FEED = Pattern.compile("(?<=\\n)");

    private TextDiff() {
    }

    public static List<Segment> words(String a, String b) {
        return diff(tokens(a), tokens(b), (deleted, inserted) -> List.of(new Segment(Op.DELETE, deleted), new Segment(Op.INSERT, inserted)));
    }

    /** Line diff; a block of replaced lines is refined to a word diff of the block. */
    public static List<Segment> lines(String a, String b) {
        return diff(lines(a), lines(b), TextDiff::words);
    }

    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = WORDS.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static List<String> lines(String text) {
        return text.isEmpty() ? List.of() : List.of(AFTER_LINE_FEED.split(text));
    }

    /** Walks the deltas in order; {@code onChange} turns a replaced block (joined) into segments. */
    private static List<Segment> diff(List<String> a, List<String> b, BiFunction<String, String, List<Segment>> onChange) {
        List<Segment> segments = new ArrayList<>();
        int position = 0;
        for (AbstractDelta<String> delta : DiffUtils.diff(a, b).getDeltas()) {
            int start = delta.getSource().getPosition();
            add(segments, Op.EQUAL, String.join("", a.subList(position, start)));
            String deleted = String.join("", delta.getSource().getLines());
            String inserted = String.join("", delta.getTarget().getLines());
            switch (delta.getType()) {
                case DELETE -> add(segments, Op.DELETE, deleted);
                case INSERT -> add(segments, Op.INSERT, inserted);
                default -> onChange.apply(deleted, inserted).forEach(s -> add(segments, s.op(), s.text()));
            }
            position = start + delta.getSource().size();
        }
        add(segments, Op.EQUAL, String.join("", a.subList(position, a.size())));
        return segments;
    }

    /** Appends a segment, merging it into the previous one when the operation is the same. */
    private static void add(List<Segment> segments, Op op, String text) {
        if (text.isEmpty()) {
            return;
        }
        int last = segments.size() - 1;
        if (last >= 0 && segments.get(last).op() == op) {
            segments.set(last, new Segment(op, segments.get(last).text() + text));
        } else {
            segments.add(new Segment(op, text));
        }
    }
}
