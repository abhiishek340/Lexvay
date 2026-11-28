package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.Finding;
import com.lexvay.common.YamlFile;
import com.lexvay.document.QuoteVerifier;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.SearchTerms;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** The golden set is data written by hand; these checks keep it well formed and tied to the seed documents. */
class GoldenFileTest {

    static final Path EVAL = Path.of("..", "eval");
    static final Path SEED = Path.of("..", "seed");

    final Golden golden = YamlFile.read(EVAL.resolve("golden.yml"), Golden.class);

    @Test
    void coversTheFortyTwoSeededRequirementsOnceEach() {
        assertThat(golden.fictional()).isTrue();
        assertThat(golden.requirements()).hasSize(42);
        assertThat(golden.requirements().stream().map(Golden.Entry::key)).doesNotHaveDuplicates();
        Map<Finding, Long> byFinding = golden.requirements().stream().collect(Collectors.groupingBy(Golden.Entry::finding, Collectors.counting()));
        assertThat(byFinding).containsEntry(Finding.EVIDENCE_FOUND, 28L).containsEntry(Finding.AMBIGUOUS, 11L)
                .containsEntry(Finding.EVIDENCE_MISSING, 3L);
        assertThat(golden.requirements().stream().filter(e -> e.type().isNumeric())).hasSize(11).allSatisfy(e -> {
            assertThat(e.numeric()).as(e.key()).isNotNull();
            // The credit hours disagree with the fewest credits of the seeded graduation paths (AC-1, D-590).
            assertThat(e.finding()).as(e.key()).isEqualTo(e.numeric().field() == NumericField.TOTAL_CREDITS ? Finding.AMBIGUOUS : Finding.EVIDENCE_FOUND);
        });
    }

    @Test
    void everyCitedDocumentExistsAndItsQuoteSubstringAppearsInTheSeedText() throws IOException {
        for (Golden.Entry e : golden.requirements()) {
            if (e.finding() == Finding.EVIDENCE_MISSING) {
                assertThat(e.document()).as(e.key() + " missing has no document").isNull();
                assertThat(e.quoteContains()).as(e.key() + " missing has no quote").isNull();
                continue;
            }
            assertThat(e.document()).as(e.key()).isNotBlank();
            assertThat(e.quoteContains()).as(e.key()).isNotBlank();
            assertThat(SEED.resolve("pdf").resolve(e.document())).as(e.key()).exists();
            Path source = SEED.resolve("docs").resolve(e.document().replace(".pdf", ".md"));
            assertThat(QuoteVerifier.normalizeWhitespace(Files.readString(source)))
                    .as(e.key() + " quote in " + source.getFileName()).contains(QuoteVerifier.normalizeWhitespace(e.quoteContains()));
        }
    }

    @Test
    void everyRequirementHasThreeToEightSearchTermsThatTheProductionNormaliserKeepsWhole() {
        for (Golden.Entry e : golden.requirements()) {
            assertThat(e.searchTerms()).as(e.key() + " search terms").hasSizeBetween(3, SearchTerms.MAX_PROPOSED).doesNotHaveDuplicates();
            assertThat(SearchTerms.normalise(e.searchTerms(), e.title(), e.text())).as(e.key() + " terms are what the extraction would store")
                    .isEqualTo(e.searchTerms());
            assertThat(e.searchTerms()).as(e.key()).allSatisfy(term -> assertThat(term).hasSizeLessThanOrEqualTo(SearchTerms.MAX_LENGTH));
        }
    }

    @Test
    void theReplaceListCarriesTheGoldenSearchTermsInTheEndpointsFormat() {
        var item = golden.requirementList("UT", new ObjectMapper()).get("requirements").get(1);
        assertThat(item.get("search_terms")).hasSize(golden.requirements().get(1).searchTerms().size());
        assertThat(item.get("search_terms").get(0).asString()).isEqualTo(golden.requirements().get(1).searchTerms().get(0));
    }

    @Test
    void thresholdsAreStrictForTheScriptedModelAndNeverLooseOnFalseEvidenceFound() {
        Thresholds thresholds = YamlFile.read(EVAL.resolve("thresholds.yml"), Thresholds.class);
        assertThat(thresholds.fake()).isEqualTo(new Thresholds.Limits(0.0, 1.0, 1.0, 1.0, 1.0));
        assertThat(thresholds.live().maxFalseEvidenceFoundRate()).isZero();
        assertThat(thresholds.live().minNumericAccuracy()).isEqualTo(1.0);
        assertThat(thresholds.retrieval()).isEqualTo(new Thresholds.Retrieval(0.95, 1.0, 0.66));
    }
}
