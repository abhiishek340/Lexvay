package com.lexvay.assessment.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.match.PassageSearch.Ranker;
import com.lexvay.common.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Search terms in the one BM25 statement (F7c): their lexemes weigh as much as the text's (1.0), a lexeme found in several
 * sources keeps the highest weight, and a page is labelled with the source of the highest-weighted lexeme that matched it. The
 * words are chosen so that the English stemmer leaves them alone, except where a test is about stemming.
 */
class PassageSearchTermsTest extends AbstractAssessmentTest {

    static final double EPSILON = 1e-6;

    @Autowired PassageSearch search;

    private UUID programWith(String... pages) {
        UUID programId = fx.program(t, List.of("UT"));
        for (int i = 0; i < pages.length; i++) {
            fx.document(t, programId, "page-" + (i + 1) + ".txt", "SYLLABUS", null, pages[i]);
        }
        return programId;
    }

    private List<Passage> search(UUID programId, String title, String text, List<String> terms, Ranker ranker) {
        return TenantContext.runAs(t.tenantId(), () -> search.search(programId, title, text, terms, 8, ranker));
    }

    @Test
    void aPageThatHasOnlyASearchTermIsFoundAndLabelledAsFoundThroughTerms() {
        // P1 "Seminar in psychopathology" is a heading line, P2 has the requirement's own word, P3 neither.
        UUID programId = programWith("Seminar in psychopathology\nweekly readings", "zebra quartz basalt", "granite marble");

        List<Passage> without = search(programId, "Diagnosis", "zebra", List.of(), Ranker.BM25);
        List<Passage> with = search(programId, "Diagnosis", "zebra", List.of("psychopathology"), Ranker.BM25);

        assertThat(without).extracting(Passage::filename).containsExactly("page-2.txt");
        assertThat(with).extracting(Passage::filename).containsExactlyInAnyOrder("page-1.txt", "page-2.txt");
        Passage found = with.stream().filter(p -> p.filename().equals("page-1.txt")).findFirst().orElseThrow();
        assertThat(found.matchedVia()).isEqualTo(MatchSource.TERMS);
        assertThat(found.matchedTerms()).containsExactly("psychopathology");
        Passage own = with.stream().filter(p -> p.filename().equals("page-2.txt")).findFirst().orElseThrow();
        assertThat(own.matchedVia()).isEqualTo(MatchSource.TEXT);
        assertThat(own.matchedTerms()).isEmpty();
    }

    @Test
    void aTermLexemeWeighsAsMuchAsATextLexemeAndTheScoreIsComputedByHand() {
        // N = 3, avglen = (2 + 2 + 2) / 3 = 2, tf 1, len 2 for every match: f = 2.2 / (1 + 1.2 * (0.25 + 0.75)) = 1.
        UUID programId = programWith("zebra marble", "quartz marble", "granite basalt");
        double idf = Math.log(1 + (3 - 1 + 0.5) / (1 + 0.5));

        List<Passage> ranked = search(programId, "", "zebra", List.of("quartz"), Ranker.BM25);

        assertThat(ranked).extracting(Passage::filename).containsExactly("page-1.txt", "page-2.txt");
        assertThat(ranked.get(0).rank()).isCloseTo(idf, offset(EPSILON));
        assertThat(ranked.get(1).rank()).as("terms boost 1.0, the same as the text").isCloseTo(idf, offset(EPSILON));
    }

    @Test
    void aLexemeInSeveralSourcesKeepsTheHighestBoostAndTheRequirementsOwnSource() {
        UUID programId = programWith("zebra marble", "quartz marble", "basalt marble", "granite marble");
        double idf = Math.log(1 + (4 - 1 + 0.5) / (1 + 0.5));

        // zebra is in the title and the terms: boost 2.0, TITLE. quartz is in the text and the terms: boost 1.0, TEXT, not summed.
        List<Passage> ranked = search(programId, "zebra", "quartz", List.of("zebra", "quartz", "basalt"), Ranker.BM25);

        assertThat(ranked).extracting(Passage::filename).containsExactly("page-1.txt", "page-2.txt", "page-3.txt");
        assertThat(ranked.get(0).rank()).isCloseTo(2.0 * idf, offset(EPSILON));
        assertThat(ranked.get(1).rank()).isCloseTo(1.0 * idf, offset(EPSILON));
        assertThat(ranked).extracting(Passage::matchedVia).containsExactly(MatchSource.TITLE, MatchSource.TEXT, MatchSource.TERMS);
    }

    @Test
    void aPageWithBothAWordOfTheTextAndATermIsLabelledByTheTextAndAddsTheTermsScore() {
        UUID programId = programWith("zebra quartz", "zebra marble", "quartz marble", "granite basalt");

        List<Passage> ranked = search(programId, "", "zebra", List.of("quartz"), Ranker.BM25);

        assertThat(ranked.get(0).filename()).isEqualTo("page-1.txt");
        assertThat(ranked.get(0).matchedVia()).isEqualTo(MatchSource.TEXT);
        assertThat(ranked.get(0).matchedTerms()).as("terms are named only for a page found through them").isEmpty();
        assertThat(ranked).extracting(Passage::filename).containsExactlyInAnyOrder("page-1.txt", "page-2.txt", "page-3.txt");
    }

    @Test
    void termsAreStemmedLikeThePagesSoOtherWordFormsMatchAndOnlyTheTermsThatMatchedAreNamed() {
        UUID programId = programWith("The course teaches diagnostic classification and psychiatric syndromes", "granite basalt");

        List<Passage> ranked = search(programId, "Diagnosis", "mental disorders", List.of("syndrome", "teaching", "marble", "unrelated words"), Ranker.BM25);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).matchedVia()).isEqualTo(MatchSource.TERMS);
        assertThat(ranked.get(0).matchedTerms()).containsExactly("syndrome", "teaching");
    }

    @Test
    void noTermsGiveTheSameShortlistScoresAndLabelsAsTheRequirementsOwnWords() {
        UUID programId = programWith("zebra zebra quartz marble", "zebra marble marble", "granite granite");

        List<Passage> ranked = search(programId, "zebra", "quartz", List.of(), Ranker.BM25);

        assertThat(ranked).extracting(Passage::filename).containsExactly("page-1.txt", "page-2.txt");
        assertThat(ranked).extracting(Passage::matchedVia).containsExactly(MatchSource.TITLE, MatchSource.TITLE);
        assertThat(ranked).allSatisfy(p -> assertThat(p.matchedTerms()).isEmpty());
    }

    @Test
    void quotesAndBackslashesInATermCannotBreakTheQuery() {
        UUID programId = programWith("zebra quartz", "granite basalt");

        List<Passage> ranked = search(programId, "zebra", "", List.of("basalt' | !(", "granite\\ ' ) & :* \""), Ranker.BM25);

        assertThat(ranked).extracting(Passage::filename).containsExactlyInAnyOrder("page-1.txt", "page-2.txt");
    }

    @Test
    void theBaselineRankerOrsTheTermsInToo() {
        UUID programId = programWith("seminar psychopathology", "zebra quartz", "granite basalt");

        List<Passage> ranked = search(programId, "Diagnosis", "zebra", List.of("psychopathology"), Ranker.TS_RANK);

        assertThat(ranked).extracting(Passage::filename).containsExactlyInAnyOrder("page-1.txt", "page-2.txt");
        assertThat(ranked).filteredOn(p -> p.filename().equals("page-1.txt")).extracting(Passage::matchedVia).containsExactly(MatchSource.TERMS);
        assertThat(ranked).filteredOn(p -> p.filename().equals("page-2.txt")).extracting(Passage::matchedVia).containsExactly(MatchSource.TEXT);
    }

    @Test
    void aSearchOfOnlyTermsFindsPagesAndAnEmptyOneFindsNothing() {
        UUID programId = programWith("zebra quartz", "granite basalt");

        assertThat(search(programId, "", "", List.of("basalt"), Ranker.BM25)).extracting(Passage::filename).containsExactly("page-2.txt");
        assertThat(search(programId, "", "", List.of("basalt"), Ranker.TS_RANK)).extracting(Passage::filename).containsExactly("page-2.txt");
        assertThat(search(programId, "", "", List.of(), Ranker.BM25)).isEmpty();
        assertThat(search(programId, "", "", List.of("the", "and"), Ranker.BM25)).isEmpty();
    }
}
