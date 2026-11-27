package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.YamlFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** `make eval`'s retrieval measurement in a test: the real seed and added syllabi, the real queries, both rankers, a real database. */
class RetrievalServiceTest extends AbstractDatabaseTest {

    static final Path SEED = Path.of("..", "seed");
    static final Path EVAL = Path.of("..", "eval");

    @Autowired RetrievalService service;

    final RetrievalQueries queries = YamlFile.read(EVAL.resolve("retrieval").resolve("queries.yml"), RetrievalQueries.class);
    final Thresholds thresholds = YamlFile.read(EVAL.resolve("thresholds.yml"), Thresholds.class);

    @Test
    void bm25MeetsTheLimitsAndIsNotWorseThanTheBaseline() {
        RetrievalReport report = service.run(SEED, EVAL.resolve("retrieval"), queries, thresholds.retrieval());

        assertThat(report.documents()).isEqualTo(14 + 13);
        assertThat(report.breaches()).isEmpty();
        assertThat(report.passed()).isTrue();
        RetrievalScorer.Metrics bm25 = report.bm25().counted();
        RetrievalScorer.Metrics baseline = report.baseline().counted();
        assertThat(bm25.recallAtDeep().value()).isGreaterThanOrEqualTo(0.95).isGreaterThanOrEqualTo(baseline.recallAtDeep().value());
        assertThat(bm25.recallAtShallow().value()).isGreaterThanOrEqualTo(baseline.recallAtShallow().value());
        assertThat(bm25.excerptCoverage().isComplete()).as("every recalled query's evidence is inside the excerpt").isTrue();
        assertThat(report.bm25().gap().recallAtDeep().denominator()).as("lexical gaps are scored apart").isGreaterThanOrEqualTo(6);
        assertThat(report.render()).contains("Recall@8", "Recall@3", "Mean reciprocal rank", "Excerpt coverage", "BM25 + terms", "RETRIEVAL: pass");
        assertThat(report.markdown()).contains("# Retrieval quality", "## Lexical gaps", "ts_rank (baseline)", "BM25 + terms");
    }

    @Test
    void searchTermsRecoverTheLexicalGapsWithoutCostingTheCountedQueries() {
        RetrievalReport report = service.run(SEED, EVAL.resolve("retrieval"), queries, thresholds.retrieval());

        RetrievalScorer.Metrics plain = report.bm25().counted();
        RetrievalScorer.Metrics withTerms = report.terms().counted();
        assertThat(withTerms.recallAtDeep().value()).isGreaterThanOrEqualTo(plain.recallAtDeep().value());
        assertThat(withTerms.recallAtShallow().value()).isGreaterThanOrEqualTo(plain.recallAtShallow().value());
        assertThat(withTerms.excerptCoverage().isComplete()).isTrue();
        assertThat(report.terms().gap().recallAtDeep().denominator()).isEqualTo(6);
        assertThat(report.terms().gap().recallAtDeep().numerator()).as("lexical-gap recall@8 with search terms: at least 4 of 6")
                .isGreaterThanOrEqualTo(4);
        assertThat(report.terms().gap().recallAtDeep().numerator()).as("and more than without them")
                .isGreaterThan(report.bm25().gap().recallAtDeep().numerator());
    }

    @Test
    void withoutSearchTermsTheLexicalGapLimitFailsSoThatTheLimitIsReal() {
        RetrievalQueries bare = new RetrievalQueries(true, queries.queries().stream()
                .map(q -> new RetrievalQueries.Query(q.id(), q.title(), q.text(), q.file(), q.page(), q.contains(), List.of(), q.lexicalGap())).toList());

        RetrievalReport report = service.run(SEED, EVAL.resolve("retrieval"), bare, thresholds.retrieval());

        assertThat(report.passed()).isFalse();
        assertThat(report.breaches()).singleElement().asString().startsWith("lexical-gap recall@8 with search terms ");
    }

    @Test
    void queriesWhoseExpectedPageIsNotThereFailTheLimitsSoTheProcessWouldExitNonZero() {
        List<String> wrong = List.of("R01", "R03", "R04");
        RetrievalQueries broken = new RetrievalQueries(true, queries.queries().stream()
                .map(q -> wrong.contains(q.id()) ? new RetrievalQueries.Query(q.id(), q.title(), q.text(), q.file(), 9, q.contains(), q.searchTerms(), false) : q)
                .toList());

        RetrievalReport report = service.run(SEED, EVAL.resolve("retrieval"), broken, thresholds.retrieval());

        assertThat(report.passed()).isFalse();
        assertThat(report.breaches()).anyMatch(b -> b.startsWith("recall@8 ") && b.endsWith(" is below 95.0 %"));
        assertThat(report.render()).contains("RETRIEVAL: FAIL", "R01");
    }
}
