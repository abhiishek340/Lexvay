package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.Finding;
import com.lexvay.common.YamlFile;
import com.lexvay.document.QuoteVerifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

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
        assertThat(byFinding).containsEntry(Finding.EVIDENCE_FOUND, 33L).containsEntry(Finding.AMBIGUOUS, 6L)
                .containsEntry(Finding.EVIDENCE_MISSING, 3L);
        assertThat(golden.requirements().stream().filter(e -> e.type().isNumeric())).hasSize(11).allSatisfy(e -> {
            assertThat(e.numeric()).as(e.key()).isNotNull();
            assertThat(e.finding()).as(e.key()).isEqualTo(Finding.EVIDENCE_FOUND);
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
    void thresholdsAreStrictForTheScriptedModelAndNeverLooseOnFalseEvidenceFound() {
        Thresholds thresholds = YamlFile.read(EVAL.resolve("thresholds.yml"), Thresholds.class);
        assertThat(thresholds.fake()).isEqualTo(new Thresholds.Limits(0.0, 1.0, 1.0, 1.0, 1.0));
        assertThat(thresholds.live().maxFalseEvidenceFoundRate()).isZero();
        assertThat(thresholds.live().minNumericAccuracy()).isEqualTo(1.0);
        assertThat(thresholds.retrieval()).isEqualTo(new Thresholds.Retrieval(0.95, 1.0));
    }
}
