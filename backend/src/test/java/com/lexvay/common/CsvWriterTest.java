package com.lexvay.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    private static String csv(String... fields) {
        return new CsvWriter().row(Arrays.asList(fields)).toString();
    }

    @Test
    void everyFieldIsQuotedAndEveryRecordEndsWithCrlf() {
        String text = new CsvWriter().row(List.of("a", "b")).row(List.of("c", "")).toString();
        assertThat(text).isEqualTo("\"a\",\"b\"\r\n\"c\",\"\"\r\n");
    }

    @Test
    void quotesAreDoubledAndSeparatorsAndLineBreaksStayInsideTheField() {
        assertThat(csv("say \"yes\"", "x,y", "line 1\nline 2")).isEqualTo("\"say \"\"yes\"\"\",\"x,y\",\"line 1\nline 2\"\r\n");
    }

    @Test
    void nullIsAnEmptyField() {
        assertThat(csv(null, "b")).isEqualTo("\"\",\"b\"\r\n");
    }

    @Test
    void aFieldASpreadsheetWouldEvaluateIsPrefixedWithAnApostrophe() {
        assertThat(csv("=1+2", "+1", "-1", "@SUM(A1)", "\tx", "\rx"))
                .isEqualTo("\"'=1+2\",\"'+1\",\"'-1\",\"'@SUM(A1)\",\"'\tx\",\"'\rx\"\r\n");
        assertThat(csv("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"\r\n");
        assertThat(csv("a=b", "1-2", "x@y")).as("only the first character matters").isEqualTo("\"a=b\",\"1-2\",\"x@y\"\r\n");
    }

    @Test
    void textIsEncodedAsUtf8() {
        byte[] bytes = new CsvWriter().row(List.of("Señora Müller", "東京", "Ω")).toBytes();
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("\"Señora Müller\",\"東京\",\"Ω\"\r\n");
        assertThat(bytes).hasSize("\"Señora Müller\",\"東京\",\"Ω\"\r\n".getBytes(StandardCharsets.UTF_8).length);
        assertThat(bytes[0]).as("no byte order mark").isEqualTo((byte) '"');
    }
}
