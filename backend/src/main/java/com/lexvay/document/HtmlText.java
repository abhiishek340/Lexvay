package com.lexvay.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * The readable text of an HTML page, for rule sources. Navigation, headers, footers, scripts and styles are
 * dropped; every block element (paragraph, heading, list item, table cell) becomes a line, so the page keeps the
 * paragraph structure and none of the markup. Lines are grouped into pages of at most {@link #PAGE_CHARS}
 * characters, never splitting a paragraph unless it is longer than a page on its own.
 */
final class HtmlText {

    static final int PAGE_CHARS = 4000;

    private HtmlText() {
    }

    static List<String> pages(byte[] html) {
        org.jsoup.nodes.Document document;
        try {
            // A null charset lets jsoup read it from the byte order mark or the meta tag, falling back to UTF-8.
            document = Jsoup.parse(new ByteArrayInputStream(html), null, "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        document.select("script, style, nav, header, footer").remove();
        Lines lines = new Lines();
        NodeTraversor.traverse(lines, document.body());
        return paginate(lines.finish());
    }

    private static List<String> paginate(List<String> lines) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String line : lines) {
            for (String piece : split(line)) {
                if (page.length() > 0 && page.length() + 1 + piece.length() > PAGE_CHARS) {
                    pages.add(page.toString());
                    page.setLength(0);
                }
                if (page.length() > 0) {
                    page.append('\n');
                }
                page.append(piece);
            }
        }
        if (page.length() > 0) {
            pages.add(page.toString());
        }
        return pages;
    }

    /** A line longer than a page is cut at the last space that fits, or at the page size when it has none. */
    private static List<String> split(String line) {
        List<String> pieces = new ArrayList<>();
        String rest = line;
        while (rest.length() > PAGE_CHARS) {
            int cut = rest.lastIndexOf(' ', PAGE_CHARS);
            cut = cut <= 0 ? PAGE_CHARS : cut;
            pieces.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        pieces.add(rest);
        return pieces;
    }

    /** Collects the text of a page one block at a time. */
    private static final class Lines implements NodeVisitor {

        private final List<String> lines = new ArrayList<>();
        private final StringBuilder current = new StringBuilder();

        @Override
        public void head(Node node, int depth) {
            if (node instanceof TextNode text) {
                current.append(text.text());
            } else if (node instanceof Element element && (element.isBlock() || element.nameIs("br"))) {
                endLine();
            }
        }

        @Override
        public void tail(Node node, int depth) {
            if (node instanceof Element element && element.isBlock()) {
                endLine();
            }
        }

        List<String> finish() {
            endLine();
            return lines;
        }

        private void endLine() {
            String line = current.toString().strip();
            current.setLength(0);
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
    }
}
