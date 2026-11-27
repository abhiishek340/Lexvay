package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The per-page ingest rule of F11 with a fake engine: text layer first, OCR for the rest, never stored text from nowhere. */
class TextExtractorOcrTest {

    private static final String SCANNED_TEXT = "COUN 605 Professional Orientation and Ethical Practice covers the ACA Code of Ethics.";
    private static final String LAYER_TEXT = "Week 3: Legal standards and confidentiality";

    final FakeOcrEngine ocr = new FakeOcrEngine();
    TextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new TextExtractor(ocr, 200);
    }

    private static byte[] scan(int pages) {
        return TestDocuments.scannedPdf(java.util.Collections.nCopies(pages, List.of("scanned page")), 72);
    }

    @Test
    void scannedPagesAreRecognisedAndStoredAsOcrWithConfidence() {
        ocr.enable().page(SCANNED_TEXT, 91.4567).page(SCANNED_TEXT + " Second page.", 66.0);
        List<ExtractedPage> pages = extractor.extract(FileType.PDF, scan(2));
        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).text()).isEqualTo(SCANNED_TEXT);
        assertThat(pages.get(0).textSource()).isEqualTo(TextSource.OCR);
        assertThat(pages.get(0).ocrConfidence()).isEqualTo(91.46);
        assertThat(pages.get(1).ocrConfidence()).isEqualTo(66.0);
        assertThat(pages.get(0).charStart()).isZero();
        assertThat(pages.get(0).charEnd()).isEqualTo(SCANNED_TEXT.length());
        assertThat(pages.get(1).charStart()).isEqualTo(SCANNED_TEXT.length() + 1);
    }

    @Test
    void textLayerPagesAreKeptAndNeverSentToTheEngine() throws IOException {
        byte[] mixed = TestDocuments.merge(TestDocuments.pdf(List.of(List.of(LAYER_TEXT))), scan(1));
        ocr.enable().page(SCANNED_TEXT, 90);
        List<ExtractedPage> pages = extractor.extract(FileType.PDF, mixed);
        assertThat(pages).extracting(ExtractedPage::textSource).containsExactly(TextSource.TEXT_LAYER, TextSource.OCR);
        assertThat(pages.get(0).text()).isEqualTo(LAYER_TEXT);
        assertThat(pages.get(0).ocrConfidence()).isNull();
        assertThat(ocr.imagesSeen()).hasSize(1);
    }

    @Test
    void pagesAreRenderedAtThreeHundredDpiInGrayscale() {
        ocr.enable().page(SCANNED_TEXT, 90);
        extractor.extract(FileType.PDF, scan(1));
        BufferedImage image = ocr.imagesSeen().get(0);
        assertThat(image.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
        assertThat(image.getWidth()).isBetween(2478, 2481);
        assertThat(image.getHeight()).isBetween(3506, 3509);
    }

    @Test
    void shortOcrResultLeavesThePageEmptyAndAnEmptyDocumentIsRejected() {
        ocr.enable().page("Page 3", 95);
        assertThatThrownBy(() -> extractor.extract(FileType.PDF, scan(1)))
                .isInstanceOf(EmptyDocumentException.class)
                .hasMessage("Document has no extractable text");
    }

    @Test
    void engineThatFindsNoWordsLeavesThePageEmptyButOtherPagesStay() {
        ocr.enable().blankPage().page(SCANNED_TEXT, 85);
        List<ExtractedPage> pages = extractor.extract(FileType.PDF, scan(2));
        assertThat(pages.get(0).text()).isEmpty();
        assertThat(pages.get(0).textSource()).isEqualTo(TextSource.TEXT_LAYER);
        assertThat(pages.get(1).textSource()).isEqualTo(TextSource.OCR);
        assertThat(pages.get(1).charStart()).isEqualTo(1);
    }

    @Test
    void unavailableEngineGivesTheScannedHint() {
        assertThatThrownBy(() -> extractor.extract(FileType.PDF, scan(2)))
                .isInstanceOf(EmptyDocumentException.class)
                .hasMessage("Document has no extractable text. It looks scanned; OCR is not enabled on this server");
        assertThat(ocr.imagesSeen()).isEmpty();
    }

    @Test
    void aThinTextLayerIsKeptWhenThereIsNoEngine() {
        byte[] thin = TestDocuments.pdf(List.of(List.of("Page 3"), List.of(LAYER_TEXT)));
        List<ExtractedPage> pages = extractor.extract(FileType.PDF, thin);
        assertThat(pages.get(0).text()).isEqualTo("Page 3");
        assertThat(pages.get(0).textSource()).isEqualTo(TextSource.TEXT_LAYER);
        assertThat(pages.get(1).text()).isEqualTo(LAYER_TEXT);
    }

    @Test
    void aThinTextLayerIsKeptWhenOcrFindsTooLittle() {
        ocr.enable().page("3", 90);
        List<ExtractedPage> pages = extractor.extract(FileType.PDF, TestDocuments.pdf(List.of(List.of("Page 3"), List.of(LAYER_TEXT))));
        assertThat(pages.get(0).text()).isEqualTo("Page 3");
        assertThat(pages.get(0).textSource()).isEqualTo(TextSource.TEXT_LAYER);
        assertThat(pages.get(0).ocrConfidence()).isNull();
        assertThat(ocr.imagesSeen()).hasSize(1);
    }

    @Test
    void aThinTextLayerIsReplacedWhenOcrReadsTheScanBetter() {
        ocr.enable().page(SCANNED_TEXT, 92);
        List<ExtractedPage> pages = extractor.extract(FileType.PDF, TestDocuments.pdf(List.of(List.of("Page 3"))));
        assertThat(pages.get(0).text()).isEqualTo(SCANNED_TEXT);
        assertThat(pages.get(0).textSource()).isEqualTo(TextSource.OCR);
    }

    @Test
    void aSingleShortWordIsStillText() {
        assertThat(extractor.extract(FileType.PDF, TestDocuments.pdf(List.of(List.of("x"))))).hasSize(1);
    }

    @Test
    void emptyTextFileGetsNoScannedHint() {
        assertThatThrownBy(() -> extractor.extract(FileType.TXT, TestDocuments.txt("   \n")))
                .isInstanceOf(EmptyDocumentException.class).hasMessage("Document has no extractable text");
    }

    @Test
    void emptyResultWithAnEngineHasNoHint() {
        ocr.enable().blankPage();
        assertThatThrownBy(() -> extractor.extract(FileType.PDF, scan(1)))
                .isInstanceOf(EmptyDocumentException.class).hasMessage("Document has no extractable text");
    }

    @Test
    void moreScannedPagesThanTheCapFailsBeforeAnyRecognition() {
        TextExtractor capped = new TextExtractor(ocr.enable(), 2);
        assertThatThrownBy(() -> capped.extract(FileType.PDF, scan(3)))
                .isInstanceOf(TooManyScannedPagesException.class)
                .hasMessageContaining("3 scanned pages").hasMessageContaining("limit is 2");
        assertThat(ocr.imagesSeen()).isEmpty();
    }

    @Test
    void thePdfPageLimitOfContentLimitsAppliesBeforeAnyRecognition() {
        ocr.enable();
        byte[] pdf = TestDocuments.pdf(java.util.Collections.nCopies(ContentLimits.MAX_PDF_PAGES + 1, List.of()));
        assertThatThrownBy(() -> new TextExtractor(ocr, 1000).extract(FileType.PDF, pdf))
                .isInstanceOf(ContentLimits.ContentLimitException.class).hasMessageContaining("limit is 500");
        assertThat(ocr.imagesSeen()).isEmpty();
    }

    @Test
    void textLayerPagesDoNotCountTowardsTheCap() throws IOException {
        byte[] mixed = TestDocuments.merge(TestDocuments.pdf(List.of(List.of(LAYER_TEXT), List.of(LAYER_TEXT + " again"))), scan(1));
        ocr.enable().page(SCANNED_TEXT, 90);
        assertThat(new TextExtractor(ocr, 1).extract(FileType.PDF, mixed)).hasSize(3);
    }

    @Test
    void anEngineFailureIsNotMistakenForAnUnparsableFile() {
        OcrEngine failing = new OcrEngine() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public Optional<OcrPage> recognize(BufferedImage page) {
                throw new OcrFailedException("Tesseract exited with code 1");
            }
        };
        assertThatThrownBy(() -> new TextExtractor(failing, 200).extract(FileType.PDF, scan(1)))
                .isInstanceOf(OcrFailedException.class).hasMessage("Tesseract exited with code 1");
    }

    @Test
    void renderDpiIsThreeHundredUnlessTheImageWouldPassTheCap() {
        assertThat(TextExtractor.renderDpi(PDRectangle.A4)).isEqualTo(300);
        PDRectangle huge = new PDRectangle(2000, 2000);
        int dpi = TextExtractor.renderDpi(huge);
        assertThat(dpi).isLessThan(300);
        double pixels = Math.ceil(huge.getWidth() * dpi / 72.0) * Math.ceil(huge.getHeight() * dpi / 72.0);
        assertThat(pixels).isLessThanOrEqualTo(TextExtractor.MAX_OCR_PIXELS);
    }

    @Test
    void anOversizedPageReachesTheEngineUnderThePixelCap() {
        ocr.enable().page(SCANNED_TEXT, 90);
        extractor.extract(FileType.PDF, TestDocuments.blankPdf(1500, 1500));
        BufferedImage image = ocr.imagesSeen().get(0);
        assertThat((long) image.getWidth() * image.getHeight()).isLessThanOrEqualTo(TextExtractor.MAX_OCR_PIXELS);
    }
}
