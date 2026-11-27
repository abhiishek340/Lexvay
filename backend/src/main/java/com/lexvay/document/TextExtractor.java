package com.lexvay.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Text extraction per page with character offsets. PDFs keep their pages; DOCX and TXT become one page
 * (D-017); HTML, which only rule sources fetch, becomes pages of about 4 000 characters split at paragraph
 * boundaries ({@link HtmlText}). Pages are joined by a single newline when offsets are computed (D-041).
 * A document whose pages are all blank (for example a scanned PDF with no text layer) is rejected, and so is one that
 * exceeds {@link ContentLimits} (a DOCX that inflates too far, a PDF with too many pages).
 */
public final class TextExtractor {

    private TextExtractor() {
    }

    public static List<ExtractedPage> extract(FileType type, byte[] bytes) {
        List<String> pages = switch (type) {
            case PDF -> pdfPages(bytes);
            case DOCX -> List.of(docxText(bytes));
            case TXT -> List.of(normalize(new String(bytes, StandardCharsets.UTF_8)));
            case HTML -> HtmlText.pages(bytes);
        };
        if (pages.stream().allMatch(String::isBlank)) {
            throw new EmptyDocumentException();
        }
        List<ExtractedPage> result = new ArrayList<>(pages.size());
        int offset = 0;
        for (int i = 0; i < pages.size(); i++) {
            String text = pages.get(i);
            if (i > 0) {
                offset += 1;
            }
            result.add(new ExtractedPage(i + 1, text, offset, offset + text.length()));
            offset += text.length();
        }
        return result;
    }

    private static List<String> pdfPages(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            ContentLimits.requirePdfPages(document.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(normalize(stripper.getText(document)));
            }
            return pages;
        } catch (ContentLimits.ContentLimitException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new UnparsableDocumentException("File could not be parsed as a PDF", e);
        }
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
