package com.lexvay.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequirementMatcherTest {

    private static Requirement req(String ref, String text) {
        return new Requirement(UUID.randomUUID(), UUID.randomUUID(), ref, "Title " + ref, text, RequirementType.SUBJECT, null, null, null, 1);
    }

    /** "before>after", "-" for a missing side, "*" when the ref changed. */
    private static String describe(RequirementMatcher.Pair p) {
        return (p.before() == null ? "-" : p.before().ref()) + ">" + (p.after() == null ? "-" : p.after().ref()) + (p.refChanged() ? "*" : "");
    }

    @Test
    void identicalRefsArePairedEvenWhenTheTextChangedCompletely() {
        var pairs = RequirementMatcher.match(List.of(req("A-1", "coursework in ethics")), List.of(req("A-1", "something else entirely")));
        assertThat(pairs).extracting(RequirementMatcherTest::describe).containsExactly("A-1>A-1");
    }

    @Test
    void aRenumberedRequirementIsPairedAndMarkedRefChanged() {
        var older = List.of(req("A-1", "coursework in professional ethics and legal issues in counseling"), req("A-2", "coursework in research methods"));
        var newer = List.of(req("A-2", "coursework in research methods"), req("A-3", "Coursework in professional ethics and legal issues in counseling."));
        var pairs = RequirementMatcher.match(older, newer);
        assertThat(pairs).extracting(RequirementMatcherTest::describe).containsExactly("A-2>A-2", "A-1>A-3*");
        assertThat(pairs.get(1).before()).isSameAs(older.get(0));
        assertThat(pairs.get(1).after()).isSameAs(newer.get(1));
    }

    @Test
    void aPairBelowTheThresholdIsNotMerged() {
        // 3 shared words of 7 distinct: far below 0.80
        var older = List.of(req("A-1", "coursework in professional ethics"));
        var newer = List.of(req("A-9", "coursework in professional practice and law"));
        assertThat(RequirementMatcher.match(older, newer)).extracting(RequirementMatcherTest::describe).containsExactly("->A-9", "A-1>-");
    }

    @Test
    void theThresholdItselfIsInclusive() {
        // 4 shared of 5 distinct words: exactly 0.80
        var older = List.of(req("A-1", "one two three four"));
        var newer = List.of(req("A-2", "one two three four five"));
        assertThat(RequirementMatcher.jaccard(RequirementMatcher.words("one two three four"), RequirementMatcher.words("one two three four five")))
                .isEqualTo(RequirementMatcher.THRESHOLD);
        assertThat(RequirementMatcher.match(older, newer)).extracting(RequirementMatcherTest::describe).containsExactly("A-1>A-2*");
    }

    @Test
    void justBelowTheThresholdIsNotMerged() {
        var older = List.of(req("A-1", "one two three four five six"));
        var newer = List.of(req("A-2", "one two three four five six seven eight"));   // 6 of 8 = 0.75
        assertThat(RequirementMatcher.match(older, newer)).extracting(RequirementMatcherTest::describe).containsExactly("->A-2", "A-1>-");
    }

    @Test
    void theBestPairWinsAndEachRequirementIsUsedOnce() {
        var older = List.of(req("A-1", "one two three four five six seven eight nine ten"), req("A-2", "one two three four five six seven eight nine"));
        var newer = List.of(req("B-1", "one two three four five six seven eight nine"));
        // B-1 is identical to A-2 (1.0) and close to A-1 (0.9): A-2 wins, A-1 is removed.
        assertThat(RequirementMatcher.match(older, newer)).extracting(RequirementMatcherTest::describe).containsExactly("A-2>B-1*", "A-1>-");
    }

    @Test
    void addedAndRemovedKeepTheDocumentedOrder() {
        var older = List.of(req("A-1", "alpha beta gamma"), req("A-2", "delta epsilon zeta"), req("A-3", "eta theta iota"));
        var newer = List.of(req("A-1", "alpha beta gamma"), req("A-4", "kappa lambda mu"), req("A-5", "nu xi omicron"));
        assertThat(RequirementMatcher.match(older, newer)).extracting(RequirementMatcherTest::describe)
                .containsExactly("A-1>A-1", "->A-4", "->A-5", "A-2>-", "A-3>-");
    }

    @Test
    void emptyTextsAreNeverSimilar() {
        assertThat(RequirementMatcher.jaccard(RequirementMatcher.words(""), RequirementMatcher.words("   "))).isZero();
    }

    @Test
    void aRefPresentInBothVersionsIsNeverAvailableForRenumberMatching() {
        var older = List.of(req("A-1", "coursework in ethics and law"), req("A-2", "coursework in ethics and law"));
        var newer = List.of(req("A-1", "coursework in ethics and law"), req("A-3", "coursework in ethics and law"));
        assertThat(RequirementMatcher.match(older, newer)).extracting(RequirementMatcherTest::describe).containsExactly("A-1>A-1", "A-2>A-3*");
    }
}
