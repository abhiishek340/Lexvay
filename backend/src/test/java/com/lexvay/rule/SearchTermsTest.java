package com.lexvay.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTermsTest {

    private static List<String> normalise(String... terms) {
        return SearchTerms.normalise(List.of(terms), "Diagnosis", "coursework in the diagnosis and treatment planning of mental disorders");
    }

    @Test
    void trimsCollapsesWhitespaceAndLowerCasesKeepingTheGivenOrder() {
        assertThat(normalise("  Psychopathology ", "Abnormal   Psychology", "DSM")).containsExactly("psychopathology", "abnormal psychology", "dsm");
    }

    @Test
    void dropsDuplicatesAfterNormalisingAndKeepsTheFirst() {
        assertThat(normalise("psychopathology", "Psychopathology ", "dsm", "DSM", "psychopathology")).containsExactly("psychopathology", "dsm");
    }

    @Test
    void dropsEmptyTerms() {
        assertThat(normalise("", "   ", "dsm")).containsExactly("dsm");
    }

    @Test
    void dropsATermWhoseEveryWordIsAlreadyInTheTitleOrTheText() {
        assertThat(normalise("Diagnosis", "mental disorders", "treatment planning", "planning of treatment", "diagnosis treatment")).isEmpty();
        // The comparison is by whole words: "disorder" is not "disorders" (the database's stemming is not available here).
        assertThat(normalise("disorder", "mental illness", "treatment plan")).containsExactly("disorder", "mental illness", "treatment plan");
    }

    @Test
    void keepsAPhraseWithAtLeastOneNewWord() {
        assertThat(normalise("diagnostic criteria", "mental health")).containsExactly("diagnostic criteria", "mental health");
    }

    @Test
    void dropsATermWithADigit() {
        assertThat(normalise("dsm 5", "dsm5", "60 hours", "dsm")).containsExactly("dsm");
    }

    @Test
    void dropsATermThatNamesAState() {
        assertThat(normalise("utah counseling act", "New York", "west virginia board", "WASHINGTON", "district of columbia", "dsm"))
                .containsExactly("dsm");
    }

    @Test
    void aStateNameIsMatchedAsAWholeWordSoOrdinaryWordsContainingOneStay() {
        // "oregano" and "washing" only begin like a state; "maine street" does name one.
        assertThat(normalise("oregano herb", "washing hands", "virginia", "marine biology", "maine street")).containsExactly("oregano herb", "washing hands", "marine biology");
    }

    @Test
    void anEmptyListStaysEmpty() {
        assertThat(normalise()).isEmpty();
    }

    @Test
    void theLimitsAreTheOnesTheSchemaAndTheConstraintUse() {
        assertThat(SearchTerms.MAX_PROPOSED).isEqualTo(8);
        assertThat(SearchTerms.MAX_STORED).isEqualTo(12);
        assertThat(SearchTerms.MAX_LENGTH).isEqualTo(40);
    }
}
