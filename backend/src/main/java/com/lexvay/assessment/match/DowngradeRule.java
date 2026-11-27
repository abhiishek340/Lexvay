package com.lexvay.assessment.match;

import com.lexvay.document.QuoteVerifier;
import com.lexvay.document.TextSource;
import java.util.List;
import java.util.Optional;

/**
 * One reason to doubt an EVIDENCE_FOUND proposal (product rule 4: when unsure, AMBIGUOUS). A rule returns the
 * reason when it applies. {@link EvidenceMatcher} applies the rules of {@link #ordered} in sequence; every rule
 * that applies appends its reason to the rationale, and any one of them turns the finding into AMBIGUOUS.
 */
@FunctionalInterface
interface DowngradeRule {

    Optional<String> reason(Passage passage, QuoteVerifier.Match quote);

    /** Elective course, thin quote, then low OCR confidence. */
    static List<DowngradeRule> ordered(int minQuoteChars, double minOcrConfidence) {
        return List.of(notRequiredCourse(), thinQuote(minQuoteChars), lowOcrConfidence(minOcrConfidence));
    }

    static DowngradeRule notRequiredCourse() {
        return (passage, quote) -> Boolean.FALSE.equals(passage.courseRequired())
                ? Optional.of("the cited course " + passage.courseCode() + " is not required of every student in this program.")
                : Optional.empty();
    }

    static DowngradeRule thinQuote(int minQuoteChars) {
        return (passage, quote) -> QuoteVerifier.normalizeWhitespace(quote.exactText()).length() < minQuoteChars
                ? Optional.of("the quoted passage is shorter than " + minQuoteChars
                        + " characters and is too thin to establish coverage on its own.")
                : Optional.empty();
    }

    /** The percentage is rounded down so that a page under the threshold never displays as the threshold. */
    static DowngradeRule lowOcrConfidence(double minOcrConfidence) {
        return (passage, quote) -> passage.textSource() == TextSource.OCR && passage.ocrConfidence() < minOcrConfidence
                ? Optional.of("the cited page was read by OCR with " + (int) Math.floor(passage.ocrConfidence())
                        + " % confidence; check the original.")
                : Optional.empty();
    }
}
