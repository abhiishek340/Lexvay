package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The retrieval metrics on a world of five queries small enough to count by hand. */
class RetrievalScorerTest {

    static RetrievalQueries.Query query(String id, String file, int page, String contains, boolean gap) {
        return new RetrievalQueries.Query(id, "title " + id, "text " + id, file, page, contains, gap);
    }

    static RetrievalScorer.Page page(String file, int page, String excerpt) {
        return new RetrievalScorer.Page(file, page, excerpt);
    }

    /** A shortlist of {@code size} filler pages with the page at 1-based position {@code at} replaced by {@code expected}. */
    static List<RetrievalScorer.Page> shortlist(int size, int at, RetrievalScorer.Page expected) {
        return IntStream.rangeClosed(1, size).mapToObj(i -> i == at ? expected : page("filler" + i + ".pdf", 1, "filler")).toList();
    }

    static final RetrievalScorer.Page A = page("a.pdf", 1, "The syllabus covers group dynamics\nand leadership.");
    static final RetrievalScorer.Page B = page("b.pdf", 2, "Week 4 Duty to warn.");
    static final RetrievalScorer.Page C = page("c.pdf", 1, "Something else entirely.");
    static final RetrievalScorer.Page D = page("d.pdf", 3, "ordinary words only");

    final List<RetrievalScorer.Result> results = List.of(
            new RetrievalScorer.Result(query("Q1", "a.pdf", 1, "group dynamics and leadership", false), shortlist(8, 1, A)),
            new RetrievalScorer.Result(query("Q2", "b.pdf", 2, "Duty to warn", false), shortlist(8, 3, B)),
            new RetrievalScorer.Result(query("Q3", "c.pdf", 1, "Something else", false), shortlist(8, 8, C)),
            new RetrievalScorer.Result(query("Q4", "d.pdf", 3, "never shown", false), shortlist(8, 2, D)),
            new RetrievalScorer.Result(query("Q5", "e.pdf", 1, "in other words", true), shortlist(8, 9, page("e.pdf", 1, "in other words"))));

    @Test
    void recallAtEightAndThreeCountTheExpectedPageInTheTopEightAndTheTopThree() {
        RetrievalScorer.Metrics m = RetrievalScorer.score(results).counted();

        assertThat(m.recallAtDeep()).isEqualTo(new Ratio(4, 4));
        assertThat(m.recallAtShallow()).as("ranks 1, 3, 8, 2: three of four are in the top three").isEqualTo(new Ratio(3, 4));
    }

    @Test
    void meanReciprocalRankAveragesOneOverTheRankOverEveryQueryOfTheGroup() {
        double expected = (1.0 + 1.0 / 3 + 1.0 / 8 + 1.0 / 2) / 4;

        assertThat(RetrievalScorer.score(results).counted().meanReciprocalRank()).isCloseTo(expected, offset(1e-12));
    }

    @Test
    void aPageBeyondTheEighthPositionCountsAsNotFoundForRecallAndRank() {
        RetrievalScorer.Metrics gap = RetrievalScorer.score(results).gap();

        assertThat(gap.recallAtDeep()).isEqualTo(new Ratio(0, 1));
        assertThat(gap.meanReciprocalRank()).isZero();
        assertThat(gap.excerptCoverage()).isEqualTo(new Ratio(0, 0));
    }

    @Test
    void excerptCoverageIsMeasuredAmongRecalledQueriesAndIgnoresWhitespaceDifferences() {
        // Q1's excerpt has a line break where the query's substring has a space (covered). Q2, Q3 are covered. Q4's substring is not in its excerpt.
        assertThat(RetrievalScorer.score(results).counted().excerptCoverage()).isEqualTo(new Ratio(3, 4));
    }

    @Test
    void lexicalGapsNeverEnterTheCountedGroup() {
        RetrievalScorer.Score score = RetrievalScorer.score(results);

        assertThat(score.counted().recallAtDeep().denominator()).isEqualTo(4);
        assertThat(score.gap().recallAtDeep().denominator()).isEqualTo(1);
    }

    @Test
    void everyQueryThatIsNotRecalledOrNotCoveredIsListed() {
        assertThat(RetrievalScorer.score(results).misses()).extracting(RetrievalScorer.Miss::query, RetrievalScorer.Miss::problem)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Q4", "excerpt of d.pdf page 3 leaves out the evidence"),
                        org.assertj.core.groups.Tuple.tuple("Q5", "expected e.pdf page 1 is not in the top 8"));
    }

    @Test
    void aShortlistShorterThanEightIsScoredAsItIs() {
        RetrievalScorer.Result shortOne = new RetrievalScorer.Result(query("Q6", "a.pdf", 1, "group dynamics", false), List.of(A));
        RetrievalScorer.Result empty = new RetrievalScorer.Result(query("Q7", "a.pdf", 1, "group dynamics", false), List.of());

        RetrievalScorer.Metrics m = RetrievalScorer.score(List.of(shortOne, empty)).counted();

        assertThat(m.recallAtDeep()).isEqualTo(new Ratio(1, 2));
        assertThat(m.meanReciprocalRank()).isCloseTo(0.5, offset(1e-12));
    }

    @Test
    void anEmptyGroupHasNothingToMeasure() {
        RetrievalScorer.Score score = RetrievalScorer.score(List.of());

        assertThat(score.counted().recallAtDeep().denominator()).isZero();
        assertThat(score.counted().meanReciprocalRank()).isZero();
    }

    @Test
    void theLimitsAreBrokenByLowRecallByFallingBelowTheBaselineAndByAnUncoveredExcerpt() {
        Thresholds.Retrieval limits = new Thresholds.Retrieval(0.95, 1.0);
        RetrievalScorer.Metrics good = new RetrievalScorer.Metrics(new Ratio(20, 20), new Ratio(18, 20), 0.9, new Ratio(20, 20));
        RetrievalScorer.Metrics baseline = new RetrievalScorer.Metrics(new Ratio(19, 20), new Ratio(18, 20), 0.8, new Ratio(20, 20));

        assertThat(Thresholds.retrievalBreaches(limits, good, baseline)).isEmpty();

        RetrievalScorer.Metrics worse = new RetrievalScorer.Metrics(new Ratio(18, 20), new Ratio(17, 20), 0.7, new Ratio(17, 18));
        assertThat(Thresholds.retrievalBreaches(limits, worse, baseline)).hasSize(4)
                .anyMatch(b -> b.startsWith("recall@8 90.0 % is below 95.0 %"))
                .anyMatch(b -> b.startsWith("recall@8 against the baseline"))
                .anyMatch(b -> b.startsWith("recall@3 against the baseline"))
                .anyMatch(b -> b.startsWith("excerpt coverage 94.4 % is below 100.0 %"));
    }
}
