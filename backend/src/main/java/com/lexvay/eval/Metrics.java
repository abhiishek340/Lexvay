package com.lexvay.eval;

/**
 * The quality numbers of one run (ROADMAP WS-EVAL). {@code falseEvidenceFound}, {@code recall},
 * {@code quoteVerification}, {@code numericAccuracy} and {@code extractionAgreement} are defined there;
 * {@code evidenceAgreement} is the extra check that the cited document and quote match the golden set.
 *
 * @param falseEvidenceFound proposed EVIDENCE_FOUND whose golden finding differs, out of proposed EVIDENCE_FOUND
 * @param recall             golden EVIDENCE_FOUND that was proposed as EVIDENCE_FOUND, out of golden EVIDENCE_FOUND
 * @param quoteVerification  stored spans whose quote QuoteVerifier finds in the stored page, out of stored spans
 * @param numericAccuracy    golden numeric requirements the rules engine answered as the golden set expects, out of golden numeric
 * @param extractionAgreement golden requirements the model extracted with the same ref and type, out of golden requirements
 * @param evidenceAgreement  golden entries naming a document whose cited document and quote match, out of those entries
 */
public record Metrics(Ratio falseEvidenceFound, Ratio recall, Ratio quoteVerification, Ratio numericAccuracy,
                      Ratio extractionAgreement, Ratio evidenceAgreement) {
}
