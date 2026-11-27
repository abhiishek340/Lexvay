package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Upload detection stays as it was; HTML is recognised only for fetched content (D-181). */
class FileTypeTest {

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void anUploadCanNeverBeHtml() {
        assertThatThrownBy(() -> FileType.detect("rules.html", bytes("<!doctype html><html></html>")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(FileType.detect("rules.txt", bytes("<html>plain text that happens to look like markup</html>"))).isEqualTo(FileType.TXT);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
            "text/html|<p>x</p>|HTML", "text/html; charset=UTF-8|x|HTML", "TEXT/HTML|x|HTML",
            "application/xhtml+xml|<html/>|HTML", "application/octet-stream|<!DOCTYPE HTML><html>|HTML",
            "|  \n<html lang=en>|HTML", "text/plain|just words|TXT"})
    void fetchedTextIsRecognised(String contentType, String body, FileType expected) {
        assertThat(FileType.detectFetched(contentType, bytes(body))).isEqualTo(expected);
    }

    @Test
    void fetchedPdfAndDocxAreRecognisedByTheirLeadingBytesWhateverTheServerSays() {
        byte[] pdf = TestDocuments.pdf(List.of(List.of("Rule")));
        byte[] docx = TestDocuments.docx(List.of("Rule"));

        assertThat(FileType.detectFetched("application/octet-stream", pdf)).isEqualTo(FileType.PDF);
        assertThat(FileType.detectFetched("text/html", pdf)).isEqualTo(FileType.PDF);
        assertThat(FileType.detectFetched(null, docx)).isEqualTo(FileType.DOCX);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {"image/png|PNGDATA", "application/json|{}", "text/plain|\u0000binary", "|plain words without a type"})
    void anythingElseIsRefused(String contentType, String body) {
        assertThatThrownBy(() -> FileType.detectFetched(contentType, bytes(body)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Unsupported content type");
    }

    @Test
    void storedFilesMapBackToTheirType() {
        for (FileType type : FileType.values()) {
            assertThat(FileType.fromFilename("rule-text." + type.extension())).isEqualTo(type);
        }
    }
}
