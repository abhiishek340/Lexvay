package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HTML as a source of rule text (D-183): page chrome is dropped, paragraphs stay lines, pages hold about 4 000 characters. */
class HtmlTextTest {

    private static final TextExtractor EXTRACTOR = new TextExtractor(new NoOcrEngine(), 200);

    private static List<ExtractedPage> extract(String html) {
        return EXTRACTOR.extract(FileType.HTML, html.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(String html) {
        return extract(html).stream().map(ExtractedPage::text).reduce((a, b) -> a + "\n" + b).orElseThrow();
    }

    @Test
    void navigationHeadersFootersScriptsAndStylesAreDropped() {
        String html = """
                <!doctype html><html><head><title>Board</title><style>p { color: red }</style><script>var x = 1;</script></head>
                <body>
                  <header>Site header</header>
                  <nav><a href="/">Home</a> <a href="/about">About</a></nav>
                  <main><h1>Counselor rules</h1><p>An applicant needs 60 semester hours.</p></main>
                  <script>track();</script>
                  <footer>Contact the board</footer>
                </body></html>
                """;

        assertThat(text(html)).isEqualTo("Counselor rules\nAn applicant needs 60 semester hours.");
    }

    @Test
    void blockElementsBecomeLinesAndInlineMarkupDoesNot() {
        String html = "<body><h2>Hours</h2><p>The <b>practicum</b> needs <a href='#'>100</a> clock hours.</p>"
                + "<ul><li>First</li><li>Second<br>third</li></ul>"
                + "<table><tr><th>Item</th><th>Hours</th></tr><tr><td>Internship</td><td>600</td></tr></table></body>";

        assertThat(text(html)).isEqualTo("Hours\nThe practicum needs 100 clock hours.\nFirst\nSecond\nthird\nItem\nHours\nInternship\n600");
    }

    @Test
    void whitespaceAndEntitiesAreNormalised() {
        assertThat(text("<body><p>  Two   spaces\n\n and&nbsp;a&amp;b  </p></body>")).isEqualTo("Two spaces and a&b");
    }

    @Test
    void theCharsetComesFromThePage() {
        byte[] latin1 = "<html><head><meta charset=\"iso-8859-1\"></head><body><p>Café hours</p></body></html>"
                .getBytes(StandardCharsets.ISO_8859_1);

        assertThat(EXTRACTOR.extract(FileType.HTML, latin1).get(0).text()).isEqualTo("Café hours");
    }

    @Test
    void aShortPageIsOnePageWithOffsets() {
        var pages = extract("<p>One</p><p>Two</p>");

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0)).isEqualTo(ExtractedPage.textLayer(1, "One\nTwo", 0, 7));
    }

    @Test
    void pagesAreSplitAtParagraphsAndNeverExceedTheLimit() {
        String paragraph = "word ".repeat(150).strip();
        StringBuilder html = new StringBuilder("<body>");
        for (int i = 0; i < 20; i++) {
            html.append("<p>").append(i).append(' ').append(paragraph).append("</p>");
        }

        var pages = extract(html + "</body>");

        assertThat(pages.size()).isGreaterThan(1);
        for (ExtractedPage page : pages) {
            assertThat(page.text().length()).isLessThanOrEqualTo(HtmlText.PAGE_CHARS);
        }
        assertThat(pages.stream().flatMap(p -> p.text().lines()))
                .as("every paragraph is whole on one page")
                .hasSize(20)
                .allSatisfy(line -> assertThat(line).endsWith("word"));
        assertThat(pages.get(1).charStart()).isEqualTo(pages.get(0).charEnd() + 1);
    }

    @Test
    void aParagraphLongerThanAPageIsSplitAtASpace() {
        String paragraph = "alpha beta gamma ".repeat(600).strip();

        var pages = extract("<p>" + paragraph + "</p>");

        assertThat(pages.size()).isEqualTo(3);
        for (ExtractedPage page : pages) {
            assertThat(page.text().length()).isLessThanOrEqualTo(HtmlText.PAGE_CHARS);
            assertThat(page.text()).matches("(alpha|beta|gamma)( (alpha|beta|gamma))*");
        }
    }

    @Test
    void aWordLongerThanAPageIsCutAtTheLimit() {
        var pages = extract("<p>" + "x".repeat(HtmlText.PAGE_CHARS + 10) + "</p>");

        assertThat(pages).extracting(p -> p.text().length()).containsExactly(HtmlText.PAGE_CHARS, 10);
    }

    @Test
    void aPageWithoutTextIsRefused() {
        assertThatThrownBy(() -> extract("<html><body><nav>Menu</nav><script>x()</script></body></html>"))
                .isInstanceOf(EmptyDocumentException.class);
    }
}
