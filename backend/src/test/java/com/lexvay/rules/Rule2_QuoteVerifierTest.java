package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.document.QuoteVerifier;
import org.junit.jupiter.api.Test;

/** Product rule 2: a quote is evidence only if it exists verbatim (whitespace-normalized) in the page. */
class Rule2_QuoteVerifierTest {

    static final String PAGE = "COUN 605 Professional Orientation and Ethics\n\n"
            + "Week 3:  Legal standards,   confidentiality and\nduty to warn.\n"
            + "Week 4: Confidentiality in group settings. Confidentiality in group settings.\n";

    @Test
    void exactQuoteIsFoundWithOriginalOffsets() {
        var match = QuoteVerifier.find(PAGE, "Professional Orientation and Ethics").orElseThrow();
        assertThat(PAGE.substring(match.charStart(), match.charEnd())).isEqualTo("Professional Orientation and Ethics");
        assertThat(match.exactText()).isEqualTo("Professional Orientation and Ethics");
    }

    @Test
    void whitespaceDifferencesAreTolerated() {
        var match = QuoteVerifier.find(PAGE, "Legal standards, confidentiality and duty to warn.").orElseThrow();
        assertThat(match.exactText()).isEqualTo("Legal standards,   confidentiality and\nduty to warn.");
        assertThat(PAGE.substring(match.charStart(), match.charEnd())).isEqualTo(match.exactText());
        assertThat(QuoteVerifier.find(PAGE, "  Legal\n\nstandards, confidentiality   and duty to warn.  ")).isPresent();
    }

    @Test
    void fabricatedOrAlteredQuotesAreRejected() {
        assertThat(QuoteVerifier.find(PAGE, "Legal standards and confidentiality")).as("words dropped").isEmpty();
        assertThat(QuoteVerifier.find(PAGE, "Students complete 600 internship hours")).as("not in page").isEmpty();
        assertThat(QuoteVerifier.find(PAGE, "professional orientation and ethics")).as("case differs").isEmpty();
    }

    @Test
    void emptyAndNullQuotesAreRejected() {
        assertThat(QuoteVerifier.find(PAGE, "")).isEmpty();
        assertThat(QuoteVerifier.find(PAGE, "   \n ")).isEmpty();
        assertThat(QuoteVerifier.find(PAGE, null)).isEmpty();
        assertThat(QuoteVerifier.find(null, "x")).isEmpty();
    }

    @Test
    void firstOfSeveralOccurrencesIsUsed() {
        var match = QuoteVerifier.find(PAGE, "Confidentiality in group settings.").orElseThrow();
        assertThat(match.charStart()).isEqualTo(PAGE.indexOf("Confidentiality in group settings."));
    }

    @Test
    void normalizeCollapsesWhitespace() {
        assertThat(QuoteVerifier.normalizeWhitespace("  a \n\n b\t c  ")).isEqualTo("a b c");
        assertThat(QuoteVerifier.normalizeWhitespace(null)).isEmpty();
    }
}
