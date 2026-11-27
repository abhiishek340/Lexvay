package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TesseractCliOcrEngineTest {

    /**
     * The one test that runs the real binary (Homebrew locally, the tesseract-ocr apt package in CI). It renders known
     * text into an image-only PDF, reads it back through the ingest path and finds the key words.
     */
    @Test
    void realTesseractReadsAnImageOnlyPdf() {
        TesseractCliOcrEngine engine = new TesseractCliOcrEngine("tesseract", "eng");
        assumeTrue(engine.available(), "tesseract is not installed");
        byte[] scan = TestDocuments.scannedPdf(List.of(List.of(
                "COUN 605 Professional Orientation and Ethical Practice.",
                "This required course covers the ACA Code of Ethics,",
                "legal standards, confidentiality and duty to warn.",
                "Practicum: 100 clock hours. Internship: 600 clock hours.")), 300);

        List<ExtractedPage> pages = new TextExtractor(engine, 200).extract(FileType.PDF, scan);

        assertThat(pages).hasSize(1);
        ExtractedPage page = pages.get(0);
        assertThat(page.textSource()).isEqualTo(TextSource.OCR);
        assertThat(page.ocrConfidence()).isBetween(80.0, 100.0);
        assertThat(page.text()).contains("Professional", "Orientation", "Ethics", "confidentiality", "Practicum", "600");
    }

    @Test
    void imageWithoutWordsGivesNoPage() {
        TesseractCliOcrEngine engine = new TesseractCliOcrEngine("tesseract", "eng");
        assumeTrue(engine.available(), "tesseract is not installed");
        BufferedImage blank = new BufferedImage(800, 600, BufferedImage.TYPE_BYTE_GRAY);
        var graphics = blank.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, 800, 600);
        graphics.dispose();
        assertThat(engine.recognize(blank)).isEmpty();
    }

    @Test
    void missingBinaryIsUnavailable() {
        assertThat(new TesseractCliOcrEngine("/nonexistent/tesseract-binary", "eng").available()).isFalse();
    }

    @Test
    void failingBinaryRaisesOcrFailedWithoutLeavingTemporaryFiles() throws IOException {
        TesseractCliOcrEngine engine = new TesseractCliOcrEngine("false", "eng");
        long before = leftoverDirectories();
        assertThatThrownBy(() -> engine.recognize(new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY)))
                .isInstanceOf(OcrFailedException.class).hasMessageContaining("exited with code 1");
        assertThat(leftoverDirectories()).isEqualTo(before);
    }

    @Test
    void languageAndBinaryAreValidated() {
        assertThatThrownBy(() -> new TesseractCliOcrEngine("tesseract", "eng; rm -rf /")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TesseractCliOcrEngine("tesseract", "-l")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TesseractCliOcrEngine(" ", "eng")).isInstanceOf(IllegalArgumentException.class);
        new TesseractCliOcrEngine("tesseract", "eng+deu");
    }

    @Test
    void engineIsChosenFromConfiguration() {
        OcrConfig config = new OcrConfig();
        assertThat(config.ocrEngine("off", "tesseract", "eng")).isInstanceOf(NoOcrEngine.class);
        assertThat(config.ocrEngine("off", "tesseract", "eng").available()).isFalse();
        assertThat(config.ocrEngine("tesseract", "tesseract", "eng")).isInstanceOf(TesseractCliOcrEngine.class);
        assertThatThrownBy(() -> config.ocrEngine("cloud", "tesseract", "eng")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NoOcrEngine().recognize(new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static long leftoverDirectories() throws IOException {
        try (Stream<Path> tmp = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return tmp.filter(p -> p.getFileName().toString().startsWith("lexvay-ocr-")).count();
        }
    }
}
