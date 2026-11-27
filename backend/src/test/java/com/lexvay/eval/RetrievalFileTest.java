package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.match.PassageExcerpter;
import com.lexvay.common.YamlFile;
import com.lexvay.document.ExtractedPage;
import com.lexvay.document.FileType;
import com.lexvay.document.QuoteVerifier;
import com.lexvay.document.TextExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The retrieval corpus and its queries are data written by hand; these checks keep them well formed, fictional and tied to the committed PDFs. */
class RetrievalFileTest {

    static final Path SEED = Path.of("..", "seed");
    static final Path RETRIEVAL = Path.of("..", "eval", "retrieval");

    final RetrievalQueries queries = YamlFile.read(RETRIEVAL.resolve("queries.yml"), RetrievalQueries.class);

    private static List<Path> addedPdfs() throws IOException {
        try (Stream<Path> files = Files.list(RETRIEVAL.resolve("pdf"))) {
            return files.filter(p -> p.toString().endsWith(".pdf")).sorted().toList();
        }
    }

    private static List<ExtractedPage> pages(Path pdf) throws IOException {
        return TextExtractor.extract(FileType.PDF, Files.readAllBytes(pdf));
    }

    private static Path pdf(String file) {
        Path added = RETRIEVAL.resolve("pdf").resolve(file);
        return Files.exists(added) ? added : SEED.resolve("pdf").resolve(file);
    }

    @Test
    void thereAreAtLeastThirtyQueriesAndSixOfThemAreLexicalGaps() {
        assertThat(queries.fictional()).isTrue();
        assertThat(queries.queries()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(queries.queries().stream().map(RetrievalQueries.Query::id)).doesNotHaveDuplicates();
        assertThat(queries.queries().stream().filter(RetrievalQueries.Query::lexicalGap)).hasSizeGreaterThanOrEqualTo(6);
        assertThat(queries.queries()).allSatisfy(q -> assertThat(q.title()).isNotBlank());
    }

    @Test
    void everyQueryNamesAPageOfACommittedPdfWhoseTextHoldsTheEvidenceSubstring() throws IOException {
        for (RetrievalQueries.Query q : queries.queries()) {
            assertThat(pdf(q.file())).as(q.id() + " file").exists();
            List<ExtractedPage> pages = pages(pdf(q.file()));
            assertThat(q.page()).as(q.id() + " page").isBetween(1, pages.size());
            assertThat(QuoteVerifier.find(pages.get(q.page() - 1).text(), q.contains())).as(q.id() + " evidence on " + q.file() + " page " + q.page()).isPresent();
        }
    }

    @Test
    void atLeastTwelveSyllabiAreAddedOfThreeToFivePagesEachEndingInTheSamePolicyPage() throws IOException {
        List<Path> added = addedPdfs();
        assertThat(added).hasSizeGreaterThanOrEqualTo(12);
        String policyPage = null;
        for (Path pdf : added) {
            List<ExtractedPage> pages = pages(pdf);
            assertThat(pages).as(pdf.getFileName().toString()).hasSizeBetween(3, 5);
            String last = QuoteVerifier.normalizeWhitespace(pages.get(pages.size() - 1).text());
            policyPage = policyPage == null ? last : policyPage;
            assertThat(last).as(pdf.getFileName() + " ends with the shared policy page").isEqualTo(policyPage)
                    .contains("Academic integrity", "Accommodations", "Grading scale");
        }
    }

    @Test
    void everyAddedPdfSaysFictionalOnPageOne() throws IOException {
        for (Path pdf : addedPdfs()) {
            assertThat(pages(pdf).get(0).text()).as(pdf.getFileName().toString()).contains("Fictional. Prototype data.");
        }
    }

    @Test
    void longPagesExistSoThatExcerptsAreExercised() throws IOException {
        long longPages = 0;
        for (Path pdf : addedPdfs()) {
            longPages += pages(pdf).stream().filter(p -> p.text().length() > PassageExcerpter.WHOLE_PAGE_CHARS).count();
        }
        assertThat(longPages).isGreaterThanOrEqualTo(12);
        long queriesOnLongPages = queries.queries().stream().filter(q -> {
            try {
                return pages(pdf(q.file())).get(q.page() - 1).text().length() > PassageExcerpter.WHOLE_PAGE_CHARS;
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }).count();
        assertThat(queriesOnLongPages).as("queries whose evidence is on a page that is excerpted").isGreaterThanOrEqualTo(8);
    }

    @Test
    void theCommittedPdfsAreTheirMarkdownSourcesSoNoneIsStale() throws IOException {
        for (Path pdf : addedPdfs()) {
            Path source = RETRIEVAL.resolve("docs").resolve(pdf.getFileName().toString().replace(".pdf", ".md"));
            String expected = QuoteVerifier.normalizeWhitespace(Files.readAllLines(source).stream()
                    .filter(line -> !line.trim().equals("---")).map(line -> line.replaceFirst("^#+\\s*", "")).collect(Collectors.joining("\n")));
            String actual = QuoteVerifier.normalizeWhitespace(pages(pdf).stream().map(ExtractedPage::text).collect(Collectors.joining("\n")));
            assertThat(actual).as(pdf.getFileName() + " matches " + source.getFileName() + " (run make seed-pdfs)").isEqualTo(expected);
        }
    }
}
