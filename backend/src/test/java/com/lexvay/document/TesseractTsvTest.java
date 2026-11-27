package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** The parser against output recorded from a real run of Tesseract 5.5.5 on a rendered page. */
class TesseractTsvTest {

    private static final String HEADER = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n";

    @Test
    void recordedOutputBecomesWordsLinesAndParagraphs() throws IOException {
        String tsv = new String(new ClassPathResource("ocr/tesseract-5.5.5-page.tsv").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        OcrPage page = TesseractTsv.parse(tsv).orElseThrow();
        assertThat(page.text()).isEqualTo("""
                COUN 605 Professional Orientation and Ethical Practice.
                This required course covers the ACA Code of Ethics,

                legal standards, confidentiality and duty to warn.

                Week 3: Practicum supervision "quoted" & 100 clock hours.""");
        assertThat(page.meanConfidence()).isCloseTo(96.36114, within(0.0001));
    }

    @Test
    void onlyWordRowsWithNonNegativeConfidenceCount() {
        String tsv = HEADER
                + "1\t1\t0\t0\t0\t0\t0\t0\t100\t100\t-1\t\n"
                + "4\t1\t1\t1\t1\t0\t0\t0\t100\t10\t-1\t\n"
                + "5\t1\t1\t1\t1\t1\t0\t0\t10\t10\t90.0\tHello\n"
                + "5\t1\t1\t1\t1\t2\t0\t0\t10\t10\t-1\tghost\n"
                + "5\t1\t1\t1\t1\t3\t0\t0\t10\t10\t70.0\t   \n"
                + "5\t1\t1\t1\t1\t4\t0\t0\t10\t10\t50.0\tworld\n";
        OcrPage page = TesseractTsv.parse(tsv).orElseThrow();
        assertThat(page.text()).isEqualTo("Hello world");
        assertThat(page.meanConfidence()).isEqualTo(70.0);
    }

    @Test
    void paragraphChangeInsideOneBlockAlsoStartsABlankLine() {
        String tsv = HEADER
                + "5\t1\t1\t1\t1\t1\t0\t0\t10\t10\t90\tfirst\n"
                + "5\t1\t1\t2\t1\t1\t0\t0\t10\t10\t90\tsecond\n"
                + "5\t1\t1\t2\t2\t1\t0\t0\t10\t10\t90\tthird\n";
        assertThat(TesseractTsv.parse(tsv).orElseThrow().text()).isEqualTo("first\n\nsecond\nthird");
    }

    @Test
    void controlCharactersAreDropped() {
        String tsv = HEADER + "5\t1\t1\t1\t1\t1\t0\t0\t10\t10\t90\tab\u0000c\u000b\r\n";
        assertThat(TesseractTsv.parse(tsv).orElseThrow().text()).isEqualTo("abc");
    }

    @Test
    void outputWithoutWordsIsEmpty() {
        assertThat(TesseractTsv.parse(HEADER + "1\t1\t0\t0\t0\t0\t0\t0\t100\t100\t-1\t\n")).isEmpty();
        assertThat(TesseractTsv.parse("")).isEmpty();
    }

    @Test
    void malformedRowsAreSkipped() {
        String tsv = HEADER + "5\t1\t1\t1\t1\t1\t0\t0\t10\t10\tnot-a-number\tbad\n"
                + "5\tshort\n"
                + "5\t1\t1\t1\t1\t2\t0\t0\t10\t10\t80\tgood\n";
        OcrPage page = TesseractTsv.parse(tsv).orElseThrow();
        assertThat(page.text()).isEqualTo("good");
        assertThat(page.meanConfidence()).isEqualTo(80.0);
    }
}
