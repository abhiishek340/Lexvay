package com.lexvay.document;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/** Builds small real PDF, DOCX and TXT files for tests. */
public final class TestDocuments {

    private TestDocuments() {
    }

    /** One page per list entry; each entry is written as lines on that page. */
    public static byte[] pdf(List<List<String>> pages) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (List<String> lines : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (lines.isEmpty()) {
                    continue;
                }
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    content.setLeading(14f);
                    content.newLineAtOffset(50, 740);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * An image-only PDF: each page is a picture of the given lines of text and has no text layer, like a scan.
     * The picture is A4 at {@code dpi}; an empty list gives a blank white page.
     */
    public static byte[] scannedPdf(List<List<String>> pages, int dpi) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (List<String> lines : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(document, scanImage(lines, dpi));
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage scanImage(List<String> lines, int dpi) {
        double scale = dpi / 72.0;
        BufferedImage image = new BufferedImage((int) (PDRectangle.A4.getWidth() * scale), (int) (PDRectangle.A4.getHeight() * scale),
                BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int fontSize = (int) Math.round(14 * scale);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
        int y = (int) Math.round(60 * scale);
        for (String line : lines) {
            g.drawString(line, (int) Math.round(50 * scale), y);
            y += (int) Math.round(fontSize * 1.8);
        }
        g.dispose();
        return image;
    }

    /** The pages of each document, in order, as one PDF. */
    public static byte[] merge(byte[]... pdfs) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        merger.setDestinationStream(out);
        for (byte[] pdf : pdfs) {
            merger.addSource(new org.apache.pdfbox.io.RandomAccessReadBuffer(pdf));
        }
        merger.mergeDocuments(org.apache.pdfbox.io.IOUtils.createMemoryOnlyStreamCache());
        return out.toByteArray();
    }

    /** A blank page of the given size in points. */
    public static byte[] blankPdf(float widthPoints, float heightPoints) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(new PDRectangle(widthPoints, heightPoints)));
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] docx(List<String> paragraphs) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] txt(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] corruptPdf() {
        return "%PDF-1.7\nthis is not a real pdf body at all".getBytes(StandardCharsets.US_ASCII);
    }
}
