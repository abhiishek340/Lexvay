package com.lexvay.seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Renders the fictional seed documents in seed/docs/*.md to text-based PDFs in seed/pdf/. A line of
 * three dashes starts a new page. Markdown heading marks are dropped; the text is otherwise written as
 * is, so quotes in seed/fake-llm.yml stay verbatim. Run with: make seed-pdfs
 */
public final class SeedPdfGenerator {

    private static final float FONT_SIZE = 11f;
    private static final float LEADING = 15f;
    private static final float MARGIN = 54f;
    private static final int WRAP_AT = 92;

    private SeedPdfGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path docs = Path.of(args.length > 0 ? args[0] : "../seed/docs");
        Path out = Path.of(args.length > 1 ? args[1] : "../seed/pdf");
        Files.createDirectories(out);
        try (Stream<Path> files = Files.list(docs)) {
            for (Path source : files.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                Path target = out.resolve(source.getFileName().toString().replace(".md", ".pdf"));
                render(Files.readAllLines(source), target);
                System.out.println("wrote " + target);
            }
        }
    }

    static void render(List<String> lines, Path target) throws IOException {
        List<List<String>> pages = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String raw : lines) {
            if (raw.trim().equals("---")) {
                pages.add(current);
                current = new ArrayList<>();
                continue;
            }
            String line = raw.replaceFirst("^#+\\s*", "");
            if (line.isBlank()) {
                current.add("");
                continue;
            }
            current.addAll(wrap(line));
        }
        pages.add(current);
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<String> pageLines : pages) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, FONT_SIZE);
                    content.setLeading(LEADING);
                    content.newLineAtOffset(MARGIN, PDRectangle.LETTER.getHeight() - MARGIN);
                    for (String line : pageLines) {
                        content.showText(sanitize(line));
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(target.toFile());
        }
    }

    static List<String> wrap(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : line.split(" ")) {
            if (current.length() + word.length() + 1 > WRAP_AT && current.length() > 0) {
                out.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    /** Standard 14 fonts have no glyphs outside WinAnsi; keep the seed text plain ASCII. */
    static String sanitize(String text) {
        return text.replace('—', '-').replace('–', '-').replace('’', '\'').replace('“', '"').replace('”', '"');
    }
}
