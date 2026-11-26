package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

    @Test
    void pdfPagesKeepTextAndCumulativeOffsets() {
        byte[] pdf = TestDocuments.pdf(List.of(
                List.of("Fictional. Prototype data.", "COUN 605 Professional Orientation and Ethics"),
                List.of("Week 3: Legal standards and confidentiality")));
        List<ExtractedPage> pages = TextExtractor.extract(FileType.PDF, pdf);
        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).pageNumber()).isEqualTo(1);
        assertThat(pages.get(0).text()).contains("COUN 605 Professional Orientation and Ethics");
        assertThat(pages.get(0).charStart()).isZero();
        assertThat(pages.get(0).charEnd()).isEqualTo(pages.get(0).text().length());
        assertThat(pages.get(1).text()).contains("Legal standards and confidentiality");
        assertThat(pages.get(1).charStart()).isEqualTo(pages.get(0).charEnd() + 1);
        assertThat(pages.get(1).charEnd()).isEqualTo(pages.get(1).charStart() + pages.get(1).text().length());
        assertThat(pages.get(0).text()).doesNotContain("\r");
    }

    @Test
    void docxBecomesOnePage() {
        byte[] docx = TestDocuments.docx(List.of("Clinical Handbook", "Practicum: 100 hours. Internship: 600 hours."));
        List<ExtractedPage> pages = TextExtractor.extract(FileType.DOCX, docx);
        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).text()).contains("Practicum: 100 hours. Internship: 600 hours.");
        assertThat(pages.get(0).charStart()).isZero();
    }

    @Test
    void txtIsOnePageWithNormalizedLineEndings() {
        List<ExtractedPage> pages = TextExtractor.extract(FileType.TXT, TestDocuments.txt("line one\r\nline two\r\n"));
        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).text()).isEqualTo("line one\nline two");
        assertThat(pages.get(0).charEnd()).isEqualTo("line one\nline two".length());
    }

    @Test
    void blankPdfIsRejectedAsEmpty() {
        byte[] blank = TestDocuments.pdf(List.of(List.of(), List.of()));
        assertThatThrownBy(() -> TextExtractor.extract(FileType.PDF, blank))
                .isInstanceOf(EmptyDocumentException.class)
                .hasMessage("Document has no extractable text");
    }

    @Test
    void corruptPdfIsRejectedAsUnparsable() {
        assertThatThrownBy(() -> TextExtractor.extract(FileType.PDF, TestDocuments.corruptPdf()))
                .isInstanceOf(UnparsableDocumentException.class)
                .hasMessageContaining("could not be parsed");
    }

    @Test
    void corruptDocxIsRejectedAsUnparsable() {
        byte[] garbage = "PK\u0003\u0004 not a zip".getBytes();
        assertThatThrownBy(() -> TextExtractor.extract(FileType.DOCX, garbage))
                .isInstanceOf(UnparsableDocumentException.class);
    }

    @Test
    void fileTypeDetectionUsesNameAndMagicBytes() {
        assertThat(FileType.detect("a.pdf", TestDocuments.pdf(List.of(List.of("x"))))).isEqualTo(FileType.PDF);
        assertThat(FileType.detect("a.docx", TestDocuments.docx(List.of("x")))).isEqualTo(FileType.DOCX);
        assertThat(FileType.detect("a.txt", TestDocuments.txt("hello"))).isEqualTo(FileType.TXT);
        assertThatThrownBy(() -> FileType.detect("a.pdf", TestDocuments.txt("not a pdf"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileType.detect("a.exe", new byte[] {1, 2, 3})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileType.detect("a.txt", new byte[] {0, 1, 2})).isInstanceOf(IllegalArgumentException.class);
    }
}
