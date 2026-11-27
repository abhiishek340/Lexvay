package com.lexvay.document;

import java.util.Optional;

/**
 * Parses the TSV that {@code tesseract <image> stdout tsv} prints (recorded from Tesseract 5.5.5). Columns:
 * level, page_num, block_num, par_num, line_num, word_num, left, top, width, height, conf, text. Words are the
 * rows of level 5 with a confidence of at least 0; the other levels carry -1. Words of one (block, paragraph,
 * line) are joined by a space, lines by a line feed and paragraphs by a blank line. The mean confidence is the
 * average over the words.
 */
final class TesseractTsv {

    private static final int COLUMNS = 12;
    private static final int LEVEL = 0;
    private static final int BLOCK = 2;
    private static final int PARAGRAPH = 3;
    private static final int LINE = 4;
    private static final int CONFIDENCE = 10;
    private static final int TEXT = 11;
    private static final String WORD_LEVEL = "5";

    private TesseractTsv() {
    }

    static Optional<OcrPage> parse(String tsv) {
        StringBuilder text = new StringBuilder();
        double confidenceSum = 0;
        int words = 0;
        String[] previous = null;
        for (String row : tsv.split("\n", -1)) {
            String[] fields = row.stripTrailing().split("\t", -1);
            if (fields.length < COLUMNS || !WORD_LEVEL.equals(fields[LEVEL])) {
                continue;
            }
            double confidence = confidence(fields[CONFIDENCE]);
            String word = clean(fields[TEXT]);
            if (confidence < 0 || word.isEmpty()) {
                continue;
            }
            if (previous != null) {
                text.append(separator(previous, fields));
            }
            text.append(word);
            confidenceSum += confidence;
            words++;
            previous = fields;
        }
        return words == 0 ? Optional.empty() : Optional.of(new OcrPage(text.toString(), confidenceSum / words));
    }

    private static String separator(String[] previous, String[] current) {
        if (!current[BLOCK].equals(previous[BLOCK]) || !current[PARAGRAPH].equals(previous[PARAGRAPH])) {
            return "\n\n";
        }
        return current[LINE].equals(previous[LINE]) ? " " : "\n";
    }

    private static double confidence(String field) {
        try {
            return Double.parseDouble(field);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Drops control characters (PostgreSQL text cannot hold NUL) and surrounding whitespace. */
    private static String clean(String word) {
        StringBuilder sb = new StringBuilder(word.length());
        word.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(sb::appendCodePoint);
        return sb.toString().strip();
    }
}
