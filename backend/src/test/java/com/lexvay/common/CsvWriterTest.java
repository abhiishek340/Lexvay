package com.lexvay.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    void aWriterWithADestinationAppendsEachRecordAsItIsAddedAndKeepsNothing() {
        StringBuilder destination = new StringBuilder();
        CsvWriter writer = CsvWriter.to(destination).row(List.of("a", "=b"));
        assertThat(destination).hasToString("\"a\",\"'=b\"\r\n");
        writer.row(Arrays.asList("c", null));
        assertThat(destination).hasToString("\"a\",\"'=b\"\r\n\"c\",\"\"\r\n");
        assertThatThrownBy(writer::toString).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFailingDestinationIsAnUncheckedIoException() {
        Appendable broken = new Appendable() {
            @Override
            public Appendable append(CharSequence csq) throws IOException {
                throw new IOException("closed");
            }

            @Override
            public Appendable append(CharSequence csq, int start, int end) throws IOException {
                throw new IOException("closed");
            }

            @Override
            public Appendable append(char c) throws IOException {
                throw new IOException("closed");
            }
        };
        assertThatThrownBy(() -> CsvWriter.to(broken).row(List.of("a"))).isInstanceOf(UncheckedIOException.class).hasMessageContaining("closed");
    }

    @Test
    void theDownloadNameCarriesTheDateInUtc() {
        assertThat(CsvWriter.attachment("lexvay-x")).isEqualTo("attachment; filename=\"lexvay-x-" + LocalDate.now(ZoneOffset.UTC) + ".csv\"");
        assertThat(CsvWriter.TEXT_CSV).hasToString("text/csv;charset=UTF-8");
    }

    @Test
    void textIsEncodedAsUtf8() {
        byte[] bytes = new CsvWriter().row(List.of("Señora Müller", "東京", "Ω")).toBytes();
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("\"Señora Müller\",\"東京\",\"Ω\"\r\n");
        assertThat(bytes).hasSize("\"Señora Müller\",\"東京\",\"Ω\"\r\n".getBytes(StandardCharsets.UTF_8).length);
        assertThat(bytes[0]).as("no byte order mark").isEqualTo((byte) '"');
    }
}
