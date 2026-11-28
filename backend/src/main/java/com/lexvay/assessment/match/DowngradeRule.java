package com.lexvay.assessment.match;

import com.lexvay.document.QuoteVerifier;
import com.lexvay.document.TextSource;
import com.lexvay.pathway.PathwayCoverage;
import java.util.List;
import java.util.Optional;

/**
 * One reason to doubt an EVIDENCE_FOUND proposal (product rule 4: when unsure, AMBIGUOUS). A rule returns the
 * reason when it applies. {@link EvidenceMatcher} applies the rules of {@link #ordered} in sequence; every rule
 * that applies appends its reason to the rationale, and any one of them turns the finding into AMBIGUOUS.
 */
@FunctionalInterface
interface DowngradeRule {

    Optional<String> reason(Passage passage, QuoteVerifier.Match quote, PathwayCoverage pathway);

    /** Course not on every graduation path, thin quote, then low OCR confidence. */
    static List<DowngradeRule> ordered(int minQuoteChars, double minOcrConfidence) {
        return List.of(notOnEveryPath(), thinQuote(minQuoteChars), lowOcrConfidence(minOcrConfidence));
    }

    /**
     * The cited course must be on every valid graduation path of the program (AC-1): a quote from a course some graduates
     * never take says nothing about them. Whatever the pathway leaves unrecorded counts against the quote (rule 4). A page
     * of a program-level document names no course and is not judged here.
     */
    static DowngradeRule notOnEveryPath() {
        return (passage, quote, pathway) -> {
            if (passage.courseId() == null) {
                return Optional.empty();
            }
            PathwayCoverage.Verdict verdict = pathway.onEveryPath(List.of(new PathwayCoverage.Course(passage.courseId(), passage.courseCode())));
            return switch (verdict.value()) {
                case TRUE -> Optional.empty();
                case FALSE -> Optional.of("the cited course " + passage.courseCode() + " is not on every graduation path.");
                case UNKNOWN -> Optional.of("the program's graduation paths are not fully known (" + String.join("; ", verdict.unknowns()) + ").");
            };
        };
    }

    static DowngradeRule thinQuote(int minQuoteChars) {
        return (passage, quote, pathway) -> QuoteVerifier.normalizeWhitespace(quote.exactText()).length() < minQuoteChars
                ? Optional.of("the quoted passage is shorter than " + minQuoteChars
                        + " characters and is too thin to establish coverage on its own.")
                : Optional.empty();
    }

    /** The percentage is rounded down so that a page under the threshold never displays as the threshold. */
    static DowngradeRule lowOcrConfidence(double minOcrConfidence) {
        return (passage, quote, pathway) -> passage.textSource() == TextSource.OCR && passage.ocrConfidence() < minOcrConfidence
                ? Optional.of("the cited page was read by OCR with " + (int) Math.floor(passage.ocrConfidence())
                        + " % confidence; check the original.")
                : Optional.empty();
    }
}
