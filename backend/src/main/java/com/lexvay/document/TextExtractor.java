package com.lexvay.document;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Text extraction per page with character offsets. PDFs keep their pages; DOCX and TXT become one page
 * (D-017); HTML, which only rule sources fetch, becomes pages of about 4 000 characters split at paragraph
 * boundaries ({@link HtmlText}). Pages are joined by a single newline when offsets are computed (D-041). A document that exceeds
 * {@link ContentLimits} (a DOCX that inflates too far, a PDF with too many pages) is rejected before any OCR.
 *
 * <p>A PDF page whose text layer has at least {@value #MIN_PAGE_CHARS} non-whitespace characters keeps it. Any
 * other page is rendered and read by the OCR engine when one is available, and kept as OCR text with its mean
 * confidence when that yields {@value #MIN_PAGE_CHARS} characters. In every other case (no engine, or too little
 * recognised) the page keeps its text layer, however short, as {@code TEXT_LAYER}: a text layer is never discarded
 * (D-164). A document whose pages all have a blank text layer after that is rejected.
 *
 * <p>Every page sent to the engine is handed to {@link OcrCheckpoints} before the next one starts, so a retried
 * ingest reuses the results of the pages that were already read (FIX-6).
 */
@Component
public class TextExtractor {

    static final int MIN_PAGE_CHARS = 20;
    static final int OCR_DPI = 300;
    static final long MAX_OCR_PIXELS = 25_000_000L;
    private static final double POINTS_PER_INCH = 72.0;

    private record PageText(String text, TextSource source, Double confidence) {

        static PageText textLayer(String text) {
            return new PageText(text, TextSource.TEXT_LAYER, null);
        }
    }

    private final OcrEngine ocr;
    private final int maxOcrPages;

    public TextExtractor(OcrEngine ocr, @Value("${lexvay.ocr.max-pages:200}") int maxOcrPages) {
        this.ocr = ocr;
        this.maxOcrPages = maxOcrPages;
    }

    public List<ExtractedPage> extract(FileType type, byte[] bytes) {
        return extract(type, bytes, OcrCheckpoints.NONE);
    }

    /** As {@link #extract(FileType, byte[])}, reading scanned pages from {@code checkpoints} and saving each one it recognises. */
    public List<ExtractedPage> extract(FileType type, byte[] bytes, OcrCheckpoints checkpoints) {
        List<PageText> pages = switch (type) {
            case PDF -> pdfPages(bytes, checkpoints);
            case DOCX -> List.of(PageText.textLayer(docxText(bytes)));
            case TXT -> List.of(PageText.textLayer(normalize(new String(bytes, StandardCharsets.UTF_8))));
            case HTML -> HtmlText.pages(bytes).stream().map(PageText::textLayer).toList();
        };
        if (pages.stream().allMatch(page -> page.text().isBlank())) {
            throw new EmptyDocumentException(type == FileType.PDF && !ocr.available());
        }
        List<ExtractedPage> result = new ArrayList<>(pages.size());
        int offset = 0;
        for (int i = 0; i < pages.size(); i++) {
            PageText page = pages.get(i);
            if (i > 0) {
                offset += 1;
            }
            result.add(new ExtractedPage(i + 1, page.text(), offset, offset + page.text().length(), page.source(),
                    page.confidence()));
            offset += page.text().length();
        }
        return result;
    }

    private List<PageText> pdfPages(byte[] bytes, OcrCheckpoints checkpoints) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            ContentLimits.requirePdfPages(document.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> layers = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                layers.add(normalize(stripper.getText(document)));
            }
            boolean recognise = ocr.available();
            long scanned = layers.stream().filter(layer -> !hasEnoughText(layer)).count();
            if (recognise && scanned > maxOcrPages) {
                throw new TooManyScannedPagesException((int) scanned, maxOcrPages);
            }
            PDFRenderer renderer = new PDFRenderer(document);
            List<PageText> pages = new ArrayList<>(layers.size());
            for (int i = 0; i < layers.size(); i++) {
                String layer = layers.get(i);
                if (hasEnoughText(layer)) {
                    pages.add(PageText.textLayer(layer));
                } else if (recognise) {
                    pages.add(recogniseScan(renderer, document.getPage(i), i, checkpoints).orElseGet(() -> PageText.textLayer(layer)));
                } else {
                    pages.add(PageText.textLayer(layer));
                }
            }
            return pages;
        } catch (ContentLimits.ContentLimitException | OcrFailedException | TooManyScannedPagesException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new UnparsableDocumentException("File could not be parsed as a PDF", e);
        }
    }

    /**
     * The OCR text of a page, or empty when the engine found too little for the page to count as read. The page is
     * read from its checkpoint when there is one at this DPI; otherwise it is recognised and checkpointed before the
     * next page starts, whatever the result, so that "enough text" is always decided here from the stored text.
     */
    private Optional<PageText> recogniseScan(PDFRenderer renderer, PDPage page, int index, OcrCheckpoints checkpoints)
            throws IOException {
        int pageNumber = index + 1;
        int dpi = renderDpi(page.getCropBox());
        Optional<OcrPage> stored = checkpoints.find(pageNumber, dpi);
        OcrPage recognised;
        if (stored.isPresent()) {
            recognised = stored.get();
        } else {
            BufferedImage image = renderer.renderImageWithDPI(index, dpi, ImageType.GRAY);
            recognised = ocr.recognize(image).map(p -> new OcrPage(normalize(p.text()), confidence(p.meanConfidence())))
                    .orElse(OcrPage.NOTHING);
            checkpoints.save(pageNumber, recognised, dpi);
        }
        return Optional.of(recognised).filter(p -> hasEnoughText(p.text()))
                .map(p -> new PageText(p.text(), TextSource.OCR, p.meanConfidence()));
    }

    /** 300 DPI, or lower for a page so large that its image would exceed {@link #MAX_OCR_PIXELS}. */
    static int renderDpi(PDRectangle box) {
        double scale = OCR_DPI / POINTS_PER_INCH;
        double pixels = box.getWidth() * scale * box.getHeight() * scale;
        if (pixels <= MAX_OCR_PIXELS) {
            return OCR_DPI;
        }
        return Math.max(1, (int) Math.floor(OCR_DPI * Math.sqrt(MAX_OCR_PIXELS / pixels)));
    }

    /** Stored as numeric(5,2). */
    private static double confidence(double meanConfidence) {
        return BigDecimal.valueOf(Math.min(100.0, Math.max(0.0, meanConfidence))).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    static boolean hasEnoughText(String text) {
        return text.codePoints().filter(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp)).count() >= MIN_PAGE_CHARS;
    }

    private static String docxText(byte[] bytes) {
        ContentLimits.requireSafeDocx(bytes);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return normalize(extractor.getText());
        } catch (IOException | RuntimeException e) {
            throw new UnparsableDocumentException("File could not be parsed as a DOCX", e);
        }
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }
}
