package com.lexvay.eval;

import com.lexvay.assessment.Finding;
import com.lexvay.rule.RequirementType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Small answer keys, rows and reports for the lab's unit tests, with counts that can be checked by hand. */
final class LabFixtures {

    static final String DOC_A = "a".repeat(64);
    static final String DOC_B = "b".repeat(64);
    static final String DOC_C = "c".repeat(64);
    static final String DOC_D = "d".repeat(64);

    private LabFixtures() {
    }

    static AnswerKey.Span span(String document, int page, int start, int end) {
        return new AnswerKey.Span(document, page, start, end);
    }

    static AnswerKey.Requirement requirement(String jurisdiction, String ref, RequirementType type, AnswerKey.Expected expected,
                                             AnswerKey.Severity severity, AnswerKey.Span... spans) {
        Golden.Numeric numeric = type.isNumeric() ? new Golden.Numeric(com.lexvay.rule.NumericField.TOTAL_CREDITS, ">=", BigDecimal.valueOf(60)) : null;
        return new AnswerKey.Requirement(ref, jurisdiction, "title " + ref, "text of " + ref, type, numeric, List.of(), expected, severity,
                List.of(spans), null, null);
    }

    static LabScorer.Cited cited(String document, int page, int start, int end, boolean onEveryPath) {
        return new LabScorer.Cited(document, page, start, end, onEveryPath);
    }

    static LabScorer.Row row(String keyId, AnswerKey.Requirement requirement, Finding finding, LabScorer.Cited cited) {
        return new LabScorer.Row(keyId, requirement, new LabScorer.Answer(finding, cited));
    }

    /**
     * Eight requirements of one key whose every metric is worked out by hand in {@code LabScorerTest}. Severe false assurance:
     * UT-3 (key missing), CO-1 (key not applicable) and CO-2 (a course not on every path, CRITICAL).
     */
    static List<LabScorer.Row> eightRows(String keyId) {
        var found = AnswerKey.Expected.EVIDENCE_FOUND;
        var critical = AnswerKey.Severity.CRITICAL;
        var standard = AnswerKey.Severity.STANDARD;
        return List.of(
                row(keyId, requirement("UT", "UT-1", RequirementType.NUMERIC, found, critical, span(DOC_A, 1, 0, 100)), Finding.EVIDENCE_FOUND,
                        cited(DOC_A, 1, 10, 60, true)),
                row(keyId, requirement("UT", "UT-2", RequirementType.SUBJECT, found, standard, span(DOC_A, 1, 200, 300)), Finding.AMBIGUOUS,
                        cited(DOC_A, 1, 200, 300, true)),
                row(keyId, requirement("UT", "UT-3", RequirementType.SUBJECT, AnswerKey.Expected.EVIDENCE_MISSING, critical), Finding.EVIDENCE_FOUND,
                        cited(DOC_B, 1, 0, 50, true)),
                row(keyId, requirement("CO", "CO-1", RequirementType.SUBJECT, AnswerKey.Expected.NOT_APPLICABLE, standard), Finding.EVIDENCE_FOUND, null),
                row(keyId, requirement("CO", "CO-2", RequirementType.SUBJECT, found, critical, span(DOC_B, 1, 0, 100)), Finding.EVIDENCE_FOUND,
                        cited(DOC_B, 1, 0, 100, false)),
                row(keyId, requirement("CO", "CO-3", RequirementType.SUPERVISED_HOURS, found, standard, span(DOC_C, 2, 0, 80)),
                        Finding.EVIDENCE_FOUND, cited(DOC_C, 2, 60, 140, true)),
                new LabScorer.Row(keyId, requirement("AZ", "AZ-1", RequirementType.SUBJECT, AnswerKey.Expected.AMBIGUOUS, standard, span(DOC_D, 1, 0, 40)),
                        LabScorer.Answer.NONE),
                row(keyId, requirement("AZ", "AZ-2", RequirementType.SUBJECT, AnswerKey.Expected.EVIDENCE_MISSING, standard), Finding.EVIDENCE_MISSING, null));
    }

    static AnswerKey key(AnswerKey.Split split, boolean fictional, boolean independent, List<AnswerKey.Requirement> requirements) {
        var labellers = independent
                ? List.of(new AnswerKey.Labeller("A", "licensed counselor educator", true), new AnswerKey.Labeller("B", "licensure board reviewer", true))
                : List.of(new AnswerKey.Labeller("A", "demo author", false));
        var bundle = new AnswerKey.SourceBundle(new AnswerKey.Program("Program", "2026-27"),
                List.of(new AnswerKey.RuleVersion("UT", "Rule set UT", LocalDate.of(2026, 7, 1))),
                List.of(new AnswerKey.BundleDocument("a.pdf", DOC_A), new AnswerKey.BundleDocument("b.pdf", DOC_B),
                        new AnswerKey.BundleDocument("c.pdf", DOC_C), new AnswerKey.BundleDocument("d.pdf", DOC_D)));
        return new AnswerKey(split, new AnswerKey.Provenance(fictional, LocalDate.of(2026, 11, 28), labellers, bundle), requirements);
    }

    static LabThresholds unsetThresholds() {
        return thresholds(null);
    }

    /** Every limit unset except severe false assurance, which is {@code severeMaximum} (null for unset). */
    static LabThresholds thresholds(Double severeMaximum) {
        var limits = new java.util.LinkedHashMap<String, LabThresholds.Limit>();
        for (LabMetric metric : LabMetric.values()) {
            limits.put(metric.key(), new LabThresholds.Limit(metric == LabMetric.SEVERE_FALSE_ASSURANCE ? severeMaximum : null));
        }
        return new LabThresholds(limits);
    }

    static EvalReport.Usage usage(boolean estimated) {
        return new EvalReport.Usage(3, 1_000, 200, estimated, BigDecimal.ZERO, Duration.ofSeconds(2));
    }

    static LabReport.Measured measured(List<LabScorer.Row> rows, Ratio quotes) {
        return new LabReport.Measured(LabScorer.score(rows), quotes, usage(false), rows, List.of());
    }

    /** A report of a run on {@code key}, with the given baseline and limits. */
    static LabReport report(EvalReport.Mode mode, AnswerKey key, LabReport.Baseline baseline, LabThresholds thresholds, List<String> breaches) {
        List<LabScorer.Row> rows = key.requirements().stream().map(r -> new LabScorer.Row("demo", r,
                new LabScorer.Answer(r.expected().finding(), null))).toList();
        return new LabReport(mode, "model-x", mode.live() ? "localhost" : null, LocalDate.of(2026, 11, 28), key.split(),
                List.of(new AnswerKeys.Loaded("demo", "f".repeat(64), key)), measured(rows, new Ratio(5, 5)), baseline, thresholds, breaches);
    }

    static Map<LabMetric, Ratio> overall(List<LabScorer.Row> rows) {
        return LabScorer.score(rows).overall();
    }
}
