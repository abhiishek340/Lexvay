package com.lexvay.eval;

import com.lexvay.assessment.Finding;
import com.lexvay.assessment.Mapping;
import com.lexvay.assessment.MappingMethod;
import com.lexvay.document.QuoteVerifier;
import com.lexvay.rule.RequirementType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compares what the pipeline produced with the golden set. Pure: no database, no model, no clock. */
public final class EvalScorer {

    /** One stored evidence span and the stored page it cites ({@code pageText} is null when the page is missing). */
    public record Span(String document, int page, String quote, String pageText) {
    }

    /** A place where the run and the golden set disagree, for the report. */
    public record Mismatch(String subject, String metric, String expected, String actual) {
    }

    public record Score(Metrics metrics, List<Mismatch> mismatches) {
    }

    private EvalScorer() {
    }

    /**
     * @param mappings  the assessment's current mappings
     * @param spans     every evidence span stored for the tenant
     * @param extracted the model's own extraction per golden key, before any replacement
     */
    public static Score score(Golden golden, List<Mapping> mappings, List<Span> spans, Map<String, RequirementType> extracted) {
        List<Mismatch> mismatches = new ArrayList<>();
        Map<String, Golden.Entry> goldenByKey = new HashMap<>();
        golden.requirements().forEach(e -> goldenByKey.put(e.key(), e));
        Map<String, Mapping> mappingByKey = new HashMap<>();
        mappings.forEach(m -> mappingByKey.put(Golden.key(m.jurisdiction(), m.requirement().ref()), m));

        int proposedFound = 0;
        int falseFound = 0;
        for (Mapping m : mappings) {
            if (m.finding() != Finding.EVIDENCE_FOUND) {
                continue;
            }
            proposedFound++;
            Golden.Entry expected = goldenByKey.get(Golden.key(m.jurisdiction(), m.requirement().ref()));
            if (expected == null || expected.finding() != Finding.EVIDENCE_FOUND) {
                falseFound++;
                mismatches.add(new Mismatch(subject(m), "false EVIDENCE_FOUND", expected == null ? "not in golden set" : expected.finding().name(),
                        Finding.EVIDENCE_FOUND.name()));
            }
        }

        int goldenFound = 0;
        int recalled = 0;
        int numeric = 0;
        int numericCorrect = 0;
        int withDocument = 0;
        int evidenceAgrees = 0;
        int extractionAgrees = 0;
        for (Golden.Entry e : golden.requirements()) {
            Mapping m = mappingByKey.get(e.key());
            Finding actual = m == null ? null : m.finding();
            if (e.finding() == Finding.EVIDENCE_FOUND) {
                goldenFound++;
                if (actual == Finding.EVIDENCE_FOUND) {
                    recalled++;
                } else {
                    mismatches.add(new Mismatch(e.key(), "recall", e.finding().name(), name(actual)));
                }
            }
            if (e.type().isNumeric()) {
                numeric++;
                if (m != null && m.method() == MappingMethod.RULES_ENGINE && actual == e.finding()) {
                    numericCorrect++;
                } else {
                    mismatches.add(new Mismatch(e.key(), "numeric accuracy", e.finding() + " by the rules engine",
                            m == null ? "no mapping" : actual + " by " + m.method()));
                }
            }
            if (e.document() != null) {
                withDocument++;
                if (cites(m, e)) {
                    evidenceAgrees++;
                } else {
                    mismatches.add(new Mismatch(e.key(), "evidence agreement", e.document() + " containing \"" + e.quoteContains() + "\"",
                            citation(m)));
                }
            }
            RequirementType extractedType = extracted.get(e.key());
            if (extractedType == e.type()) {
                extractionAgrees++;
            } else {
                mismatches.add(new Mismatch(e.key(), "extraction agreement", e.type().name(), extractedType == null ? "not extracted" : extractedType.name()));
            }
        }

        Metrics metrics = new Metrics(new Ratio(falseFound, proposedFound), new Ratio(recalled, goldenFound),
                verifiedQuotes(spans, mismatches), new Ratio(numericCorrect, numeric),
                new Ratio(extractionAgrees, golden.requirements().size()), new Ratio(evidenceAgrees, withDocument));
        return new Score(metrics, mismatches);
    }

    /** Stored spans whose quote QuoteVerifier finds in the stored page they cite, out of all stored spans; each failure is added to {@code mismatches}. */
    static Ratio verifiedQuotes(List<Span> spans, List<Mismatch> mismatches) {
        int verified = 0;
        for (Span s : spans) {
            if (s.pageText() != null && QuoteVerifier.find(s.pageText(), s.quote()).isPresent()) {
                verified++;
            } else {
                mismatches.add(new Mismatch(s.document() + " page " + s.page(), "quote verification", "quote found in stored page", "not found"));
            }
        }
        return new Ratio(verified, spans.size());
    }

    /** The cited document is the golden one and its quote contains the golden substring (whitespace-insensitive). */
    private static boolean cites(Mapping m, Golden.Entry e) {
        if (m == null || m.evidence() == null || !e.document().equals(m.evidence().filename())) {
            return false;
        }
        return QuoteVerifier.normalizeWhitespace(m.evidence().quote()).contains(QuoteVerifier.normalizeWhitespace(e.quoteContains()));
    }

    private static String citation(Mapping m) {
        return m == null || m.evidence() == null ? "no evidence cited" : m.evidence().filename() + " containing \"" + m.evidence().quote() + "\"";
    }

    private static String subject(Mapping m) {
        return Golden.key(m.jurisdiction(), m.requirement().ref());
    }

    private static String name(Finding finding) {
        return finding == null ? "no mapping" : finding.name();
    }
}
