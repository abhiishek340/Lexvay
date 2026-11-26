package com.lexvay.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Minimal paginated text layout on PDFBox (D-067): headings, paragraphs, blank lines, automatic page breaks. */
final class PdfTextWriter {

    private static final float MARGIN = 54f;
    private static final float BODY = 10.5f;
    private static final float HEADING = 14f;
    private static final float LEADING = 14f;
    private static final int WRAP = 100;

    private final PDDocument document = new PDDocument();
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDPageContentStream content;
    private float y;

    PdfTextWriter() throws IOException {
        newPage();
    }

    void heading(String text) throws IOException {
        ensureRoom(2);
        line(text, bold, HEADING);
        blank();
    }

    void label(String label, String value) throws IOException {
        paragraph(label + ": " + value, bold, regular);
    }

    void paragraph(String text) throws IOException {
        paragraph("", regular, regular);
        for (String line : wrap(text)) {
            ensureRoom(1);
            line(line, regular, BODY);
        }
    }

    private void paragraph(String text, PDType1Font first, PDType1Font rest) throws IOException {
        List<String> lines = wrap(text);
        for (int i = 0; i < lines.size(); i++) {
            ensureRoom(1);
            line(lines.get(i), i == 0 ? first : rest, BODY);
        }
    }

    void blank() throws IOException {
        y -= LEADING;
        if (y < MARGIN) {
            newPage();
        }
    }

    byte[] finish() throws IOException {
        content.close();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            document.close();
            return out.toByteArray();
        }
    }

    private void line(String text, PDType1Font font, float size) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(MARGIN, y);
        content.showText(sanitize(text));
        content.endText();
        y -= LEADING;
    }

    private void ensureRoom(int lines) throws IOException {
        if (y - lines * LEADING < MARGIN) {
            newPage();
        }
    }

    private void newPage() throws IOException {
        if (content != null) {
            content.close();
        }
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        y = PDRectangle.LETTER.getHeight() - MARGIN;
    }

    static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.replace("\r", "").split("\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : raw.split(" ")) {
                if (current.length() + word.length() + 1 > WRAP && current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                if (current.length() > 0) {
                    current.append(' ');
                }
                current.append(word);
            }
            out.add(current.toString());
        }
        return out;
    }

    /** Standard 14 fonts cover WinAnsi only; replace what they cannot show. */
    static String sanitize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.replace('—', '-').replace('–', '-').replace('’', '\'').replace('“', '"')
                .replace('”', '"').replace('→', '>').replace('•', '-').toCharArray()) {
            sb.append(c < 32 || c > 255 ? '?' : c);
        }
        return sb.toString();
    }

    static byte[] render(ReportContent report) {
        try {
            PdfTextWriter w = new PdfTextWriter();
            w.heading(report.title());
            for (var entry : report.header()) {
                w.label(entry[0], entry[1]);
            }
            w.blank();
            for (ReportContent.Item item : report.items()) {
                w.paragraph(item.heading(), w.bold, w.bold);
                for (String line : item.lines()) {
                    w.paragraph(line);
                }
                w.blank();
            }
            w.blank();
            w.paragraph(report.footer());
            return w.finish();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
