package com.lexvay.eval;

import com.lexvay.assessment.Finding;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Scores what a run answered against an answer key. Pure: no database, no model, no clock, so every definition here is
 * covered by hand-computed fixtures.
 *
 * <p>Definitions, each a count out of a total:
 * <ul>
 * <li>Severe false assurance: out of every requirement in the key, those answered EVIDENCE_FOUND where the key says
 * EVIDENCE_MISSING or NOT_APPLICABLE, or whose cited quote is from a course that is not on every student's path while the
 * requirement is CRITICAL. The denominator is every requirement, so it does not depend on what the system chose to answer.</li>
 * <li>Finding accuracy: out of the requirements the key does not call NOT_APPLICABLE (the system has no such finding), the answer equals the key.</li>
 * <li>Abstention rate: out of the requirements the key decides (EVIDENCE_FOUND or EVIDENCE_MISSING), those answered AMBIGUOUS.</li>
 * <li>Evidence precision: out of the answers that cite a span, those whose span overlaps an accepted span of the key.</li>
 * <li>Evidence recall: out of the requirements for which the key accepts a span, those where the answer cites an overlapping one.</li>
 * <li>Numeric accuracy: out of the numeric requirements the key decides on (not NOT_APPLICABLE), the answer equals the key.</li>
 * </ul>
 * A requirement the run did not answer at all (no mapping) counts as wrong wherever it is in a denominator, and never as an assurance.
 */
public final class LabScorer {

    /** The evidence an answer cites, located in the stored page text; {@code onEveryPath} is false for a course not every student takes. */
    public record Cited(String document, int page, int charStart, int charEnd, boolean onEveryPath) {
    }

    /** What one requirement was answered; {@code finding} is null when the model gave no usable answer. */
    public record Answer(Finding finding, Cited cited) {

        public static final Answer NONE = new Answer(null, null);
    }

    /** One requirement of one key with what was answered for it. */
    public record Row(String keyId, AnswerKey.Requirement requirement, Answer answer) {

        public boolean severeFalseAssurance() {
            if (answer.finding() != Finding.EVIDENCE_FOUND) {
                return false;
            }
            AnswerKey.Expected expected = requirement.expected();
            boolean unsupported = expected == AnswerKey.Expected.EVIDENCE_MISSING || expected == AnswerKey.Expected.NOT_APPLICABLE;
            boolean notOnEveryPath = requirement.severity() == AnswerKey.Severity.CRITICAL && answer.cited() != null
                    && !answer.cited().onEveryPath();
            return unsupported || notOnEveryPath;
        }

        /** True when the answer cites a span and it overlaps one the key accepts. */
        public boolean evidenceMatches() {
            Cited c = answer.cited();
            return c != null && requirement.spans().stream().anyMatch(s -> s.overlaps(c.document(), c.page(), c.charStart(), c.charEnd()));
        }

        private boolean correct() {
            return answer.finding() != null && answer.finding() == requirement.expected().finding();
        }
    }

    /** The metrics of all rows, and of the rows of each state, requirement type and severity (and key, when there is more than one). */
    public record Score(Map<LabMetric, Ratio> overall, Map<String, Map<String, Map<LabMetric, Ratio>>> breakdowns) {
    }

    private LabScorer() {
    }

    public static Score score(List<Row> rows) {
        Map<String, Function<Row, String>> dimensions = new LinkedHashMap<>();
        dimensions.put("state", r -> r.requirement().jurisdiction());
        dimensions.put("requirementType", r -> r.requirement().type().name());
        dimensions.put("severity", r -> r.requirement().severity().name());
        if (rows.stream().map(Row::keyId).distinct().count() > 1) {
            dimensions.put("key", Row::keyId);
        }
        Map<String, Map<String, Map<LabMetric, Ratio>>> breakdowns = new LinkedHashMap<>();
        dimensions.forEach((name, valueOf) -> {
            Map<String, List<Row>> byValue = new TreeMap<>();
            rows.forEach(r -> byValue.computeIfAbsent(valueOf.apply(r), v -> new ArrayList<>()).add(r));
            Map<String, Map<LabMetric, Ratio>> metrics = new LinkedHashMap<>();
            byValue.forEach((value, group) -> metrics.put(value, metrics(group)));
            breakdowns.put(name, metrics);
        });
        return new Score(metrics(rows), breakdowns);
    }

    static Map<LabMetric, Ratio> metrics(List<Row> rows) {
        int severe = 0;
        int accurate = 0;
        int accurateOf = 0;
        int abstained = 0;
        int abstainedOf = 0;
        int cited = 0;
        int citedRight = 0;
        int accepted = 0;
        int acceptedFound = 0;
        int numeric = 0;
        int numericRight = 0;
        for (Row r : rows) {
            severe += r.severeFalseAssurance() ? 1 : 0;
            boolean expectedCountable = r.requirement().expected() != AnswerKey.Expected.NOT_APPLICABLE;
            if (expectedCountable) {
                accurateOf++;
                accurate += r.correct() ? 1 : 0;
                if (r.requirement().type().isNumeric()) {
                    numeric++;
                    numericRight += r.correct() ? 1 : 0;
                }
            }
            if (r.requirement().expected().decided()) {
                abstainedOf++;
                abstained += r.answer().finding() == Finding.AMBIGUOUS ? 1 : 0;
            }
            if (r.answer().cited() != null) {
                cited++;
                citedRight += r.evidenceMatches() ? 1 : 0;
            }
            if (!r.requirement().spans().isEmpty()) {
                accepted++;
                acceptedFound += r.evidenceMatches() ? 1 : 0;
            }
        }
        Map<LabMetric, Ratio> out = new EnumMap<>(LabMetric.class);
        out.put(LabMetric.SEVERE_FALSE_ASSURANCE, new Ratio(severe, rows.size()));
        out.put(LabMetric.FINDING_ACCURACY, new Ratio(accurate, accurateOf));
        out.put(LabMetric.ABSTENTION_RATE, new Ratio(abstained, abstainedOf));
        out.put(LabMetric.EVIDENCE_PRECISION, new Ratio(citedRight, cited));
        out.put(LabMetric.EVIDENCE_RECALL, new Ratio(acceptedFound, accepted));
        out.put(LabMetric.NUMERIC_ACCURACY, new Ratio(numericRight, numeric));
        return out;
    }
}
