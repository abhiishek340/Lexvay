package com.lexvay.eval;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * Shape of eval/lab-thresholds.yml: one pre-agreed limit per {@link LabMetric}, a rate between 0 and 1 or the word
 * {@code unset}. A limit is agreed before a run, never fitted to it; a metric nobody has measured yet is {@code unset} and
 * neither passes nor fails a run. A limit applies to the pipeline's point estimate, which the report prints beside its interval.
 */
public record LabThresholds(Map<String, Limit> limits) {

    public enum Status {
        OK, FAIL, UNSET
    }

    public record Limit(Double value) {

        static final String UNSET = "unset";

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static Limit of(JsonNode node) {
            if (node.isString() && UNSET.equals(node.asString())) {
                return new Limit(null);
            }
            if (node.isNumber() && node.asDouble() >= 0 && node.asDouble() <= 1) {
                return new Limit(node.asDouble());
            }
            throw new IllegalArgumentException("a limit is a rate between 0 and 1, or the word unset: " + node);
        }

        public boolean isSet() {
            return value != null;
        }
    }

    public LabThresholds {
        Set<String> expected = Arrays.stream(LabMetric.values()).map(LabMetric::key).collect(Collectors.toSet());
        if (!limits.keySet().equals(expected)) {
            throw new IllegalArgumentException("lab-thresholds.yml must give exactly one limit for each of " + expected + ", not " + limits.keySet());
        }
    }

    public Limit limit(LabMetric metric) {
        return limits.get(metric.key());
    }

    public Status status(LabMetric metric, Ratio ratio) {
        Limit limit = limit(metric);
        if (!limit.isSet()) {
            return Status.UNSET;
        }
        boolean ok = metric.direction() == LabMetric.Direction.MAXIMUM
                ? Thresholds.withinMaximum(ratio, limit.value()) : Thresholds.meetsMinimum(ratio, limit.value());
        return ok ? Status.OK : Status.FAIL;
    }

    /** Every limit the metrics break, in plain language. Empty means the run passes. */
    public List<String> breaches(Map<LabMetric, Ratio> metrics) {
        List<String> out = new ArrayList<>();
        for (LabMetric metric : LabMetric.values()) {
            if (status(metric, metrics.get(metric)) == Status.FAIL) {
                Ratio ratio = metrics.get(metric);
                out.add(metric.label().toLowerCase(Locale.ROOT) + " "
                        + (ratio.denominator() == 0 ? "cannot be measured: the key has nothing to compare" : ratio.percent() + " (" + ratio.counts() + ") breaks the limit "
                        + metric.direction().symbol() + " " + Thresholds.percent(limit(metric).value())));
            }
        }
        return out;
    }

    /** The limit as the report prints it. */
    public String describe(LabMetric metric) {
        Limit limit = limit(metric);
        return limit.isSet() ? metric.direction().symbol() + " " + Thresholds.percent(limit.value()) : "unset";
    }
}
