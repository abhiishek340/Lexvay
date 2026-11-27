package com.lexvay.assessment.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.offset;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.common.TenantContext;
import com.lexvay.assessment.match.PassageSearch.Ranker;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * BM25 ordering on corpora small enough to score by hand (D-170): k1 = 1.2, b = 0.75, title boost 2.0, text boost 1.0,
 * idf = ln(1 + (N - df + 0.5) / (df + 0.5)), len = number of distinct lexemes of the page. The words are chosen so that
 * the English stemmer leaves them alone.
 */
class PassageRankingTest extends AbstractAssessmentTest {

    static final double EPSILON = 1e-6;

    @Autowired PassageSearch search;

    private UUID programWith(String... pages) {
        UUID programId = fx.program(t, List.of("UT"));
        for (int i = 0; i < pages.length; i++) {
            fx.document(t, programId, "page-" + (i + 1) + ".txt", "SYLLABUS", null, pages[i]);
        }
        return programId;
    }

    private List<Passage> rank(UUID programId, String title, String text, Ranker ranker) {
        return TenantContext.runAs(t.tenantId(), () -> search.search(programId, title, text, List.of(), 8, ranker));
    }

    /** f(tf, len) = tf * (k1 + 1) / (tf + k1 * (1 - b + b * len / avglen)), written out for the numbers of the corpus below. */
    @Test
    void scoresThreePagesAsComputedByHand() {
        // P1 "zebra zebra quartz marble": zebra tf 2, quartz tf 1, marble tf 1, len 3
        // P2 "zebra marble marble marble marble": zebra tf 1, marble tf 4, len 2
        // P3 "quartz basalt basalt basalt": quartz tf 1, basalt tf 3, len 2
        // P4 "granite granite": no query lexeme, len 1
        UUID programId = programWith("zebra zebra quartz marble", "zebra marble marble marble marble",
                "quartz basalt basalt basalt", "granite granite");
        // Query lexemes: zebra (title and text, so boost 2.0) and quartz (text only, boost 1.0).
        // N = 4, avglen = (3 + 2 + 2 + 1) / 4 = 2.0, df(zebra) = 2 (P1, P2), df(quartz) = 2 (P1, P3),
        // so both idf = ln(1 + (4 - 2 + 0.5) / (2 + 0.5)) = ln 2.
        double idf = Math.log(2);
        // P1: zebra  2 * 2.2 / (2 + 1.2 * (0.25 + 0.75 * 3 / 2)) = 4.4 / 3.65;  quartz 1 * 2.2 / (1 + 1.65) = 2.2 / 2.65
        double p1 = idf * (2.0 * (4.4 / 3.65) + 1.0 * (2.2 / 2.65));
        // P2: zebra  1 * 2.2 / (1 + 1.2 * (0.25 + 0.75 * 2 / 2)) = 2.2 / 2.2 = 1
        double p2 = idf * (2.0 * 1.0);
        // P3: quartz 1 * 2.2 / (1 + 1.2 * (0.25 + 0.75 * 2 / 2)) = 1
        double p3 = idf * (1.0 * 1.0);

        List<Passage> ranked = rank(programId, "zebra", "quartz zebra", Ranker.BM25);

        assertThat(ranked).extracting(Passage::filename).containsExactly("page-1.txt", "page-2.txt", "page-3.txt");
        assertThat(ranked.get(0).rank()).isCloseTo(p1, offset(EPSILON));
        assertThat(ranked.get(1).rank()).isCloseTo(p2, offset(EPSILON));
        assertThat(ranked.get(2).rank()).isCloseTo(p3, offset(EPSILON));
        assertThat(p1).as("sanity check of the hand computation").isCloseTo(2.2466, offset(1e-4));
    }

    @Test
    void aLexemeOfTheTitleCountsDoubleAndOneInBothCountsOnce() {
        // A "zebra marble" and B "quartz marble" are alike (len 2, df 1 each) except for where the query's lexemes come from.
        UUID programId = programWith("zebra marble", "quartz marble", "granite basalt", "basalt granite");
        double idf = Math.log(1 + (4 - 1 + 0.5) / (1 + 0.5));
        // N = 4, avglen = 2, tf 1, len 2: f = 2.2 / (1 + 1.2 * (0.25 + 0.75)) = 1.
        List<Passage> ranked = rank(programId, "zebra", "quartz", Ranker.BM25);

        assertThat(ranked).extracting(Passage::filename).containsExactly("page-1.txt", "page-2.txt");
        assertThat(ranked.get(0).rank()).isCloseTo(2.0 * idf, offset(EPSILON));
        assertThat(ranked.get(1).rank()).isCloseTo(1.0 * idf, offset(EPSILON));

        List<Passage> both = rank(programId, "zebra", "zebra", Ranker.BM25);
        assertThat(both).hasSize(1);
        assertThat(both.get(0).rank()).as("max of the two boosts, not their sum").isCloseTo(2.0 * idf, offset(EPSILON));
    }

    @Test
    void aWordThatIsInEveryPageCountsForLessThanARareOne() {
        // "counsel" is in every page, "polygraph" in one: the page with the rare word wins although "counsel" is repeated elsewhere.
        UUID programId = programWith("counsel counsel counsel counsel counsel", "counsel polygraph", "counsel basalt");
        List<Passage> ranked = rank(programId, "counsel polygraph", "", Ranker.BM25);

        assertThat(ranked.get(0).filename()).isEqualTo("page-2.txt");
    }

    @Test
    void tiesBreakByDocumentCreationThenPage() {
        UUID programId = programWith("zebra quartz", "quartz zebra", "granite basalt");
        List<Passage> ranked = rank(programId, "zebra", "quartz", Ranker.BM25);

        assertThat(ranked).extracting(Passage::filename).containsExactly("page-1.txt", "page-2.txt");
        assertThat(ranked.get(0).rank()).isEqualTo(ranked.get(1).rank());
    }

    @Test
    void aQueryWithNoIndexableWordFindsNothingUnderEitherRanker() {
        UUID programId = programWith("zebra quartz", "granite basalt");

        assertThat(rank(programId, "the", "and of to", Ranker.BM25)).isEmpty();
        assertThat(rank(programId, "the", "and of to", Ranker.TS_RANK)).isEmpty();
        assertThat(rank(programId, "", "", Ranker.BM25)).isEmpty();
    }

    @Test
    void quotesAndBackslashesInARequirementCannotBreakTheQuery() {
        UUID programId = programWith("zebra quartz", "granite basalt");

        assertThat(rank(programId, "zebra' | !(", "quartz\\ ' ) & :* \"", Ranker.BM25)).extracting(Passage::filename)
                .containsExactly("page-1.txt");
    }

    @Test
    void theBaselineRankerStillAnswersFromTheSameStatement() {
        UUID programId = programWith("zebra zebra quartz marble", "zebra marble", "granite granite");
        List<Passage> ranked = rank(programId, "zebra", "quartz", Ranker.TS_RANK);

        assertThat(ranked).extracting(Passage::filename).containsExactly("page-1.txt", "page-2.txt");
        assertThat(ranked.get(0).rank()).isGreaterThan(ranked.get(1).rank());
    }

    @Test
    void thePropertyNamesTheRanker() {
        assertThat(Ranker.fromProperty("bm25")).isEqualTo(Ranker.BM25);
        assertThat(Ranker.fromProperty(" TS_RANK ")).isEqualTo(Ranker.TS_RANK);
        assertThatIllegalArgumentException().isThrownBy(() -> Ranker.fromProperty("tfidf")).withMessageContaining("bm25 or ts_rank");
    }

    @Test
    void theConfiguredRankerIsBm25() {
        UUID programId = programWith("zebra zebra quartz marble", "zebra marble marble", "granite granite");
        List<Passage> configured = TenantContext.runAs(t.tenantId(), () -> search.search(programId, "zebra", "quartz", List.of(), 8));

        assertThat(configured).extracting(Passage::rank).containsExactlyElementsOf(
                rank(programId, "zebra", "quartz", Ranker.BM25).stream().map(Passage::rank).toList());
    }

    @Test
    void anotherTenantsPagesNeverEnterTheCorpus() {
        UUID programId = programWith("zebra quartz", "granite basalt");
        var other = tenants.create("assess");
        UUID otherProgram = fx.program(other, List.of("UT"));
        fx.document(other, otherProgram, "zebra-everywhere.txt", "SYLLABUS", null, "zebra zebra zebra quartz quartz");

        List<Passage> ranked = rank(programId, "zebra", "quartz", Ranker.BM25);

        assertThat(ranked).extracting(Passage::filename).containsExactly("page-1.txt");
        // N = 2, avglen = 2, df 1 for both lexemes, f = 1: the other tenant's page would have changed N, df and avglen.
        assertThat(ranked.get(0).rank()).isCloseTo((2.0 + 1.0) * Math.log(2), offset(EPSILON));
    }
}
