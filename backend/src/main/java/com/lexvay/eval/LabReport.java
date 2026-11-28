package com.lexvay.eval;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Everything one evaluation-lab run found, as Markdown for people and JSON for {@code scripts/eval-diff}. The Markdown
 * starts by saying what the numbers are worth: a fictional key, labels that are not independent, scripted answers and a dev
 * split each make the result no basis for an accuracy claim, and the report lists every reason that applies (D-621).
 *
 * @param host the host the model was called at; null when no endpoint was configured (the scripted model, the Claude API)
 */
public record LabReport(EvalReport.Mode mode, String model, String host, LocalDate date, AnswerKey.Split split,
                        List<AnswerKeys.Loaded> keys, Measured pipeline, Baseline baseline, LabThresholds thresholds,
                        List<String> breaches) {

    /** The numbers of one system, pooled over the run's keys. {@code rows} is every requirement with what was answered for it. */
    public record Measured(LabScorer.Score score, Ratio quoteVerification, EvalReport.Usage usage, List<LabScorer.Row> rows,
                           List<String> notes) {
    }

    /** The generic model's side of the comparison: not asked for, blocked with a reason, or measured. */
    public record Baseline(Status status, String reason, Measured measured) {

        public enum Status {
            NOT_REQUESTED, BLOCKED, MEASURED
        }

        public static Baseline notRequested() {
            return new Baseline(Status.NOT_REQUESTED, "not asked for (--baseline)", null);
        }

        public static Baseline blocked(String reason) {
            return new Baseline(Status.BLOCKED, reason, null);
        }

        public static Baseline measured(Measured measured) {
            return new Baseline(Status.MEASURED, null, measured);
        }
    }

    public static final String FORMAT = "lexvay-eval-lab-report/1";

    public boolean passed() {
        return breaches.isEmpty();
    }

    /** Why no accuracy claim may rest on this run; empty only for a real key, independent labels, a model and the holdout. */
    public List<String> claimBlockers() {
        List<String> out = new ArrayList<>();
        if (keys.stream().anyMatch(k -> k.key().provenance().fictional())) {
            out.add("the key is fictional");
        }
        if (keys.stream().anyMatch(k -> k.key().provenance().labellers().stream().filter(AnswerKey.Labeller::independent).count() < 2)) {
            out.add("the labels are not independent (a key needs at least two independent labellers)");
        }
        if (mode == EvalReport.Mode.SCRIPTED) {
            out.add("the answers are scripted, not a model's");
        }
        if (split == AnswerKey.Split.dev) {
            out.add("dev keys are the ones tuned against; only a holdout run can support a claim");
        }
        return out;
    }

    /** {@code <date>-<mode>-<split>}, the name both report files share. */
    public String fileStem() {
        return date + "-" + mode.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-" + split;
    }

    public String markdown() {
        StringBuilder out = new StringBuilder();
        boolean fictional = keys.stream().anyMatch(k -> k.key().provenance().fictional());
        out.append("# Lexvay evaluation lab").append(fictional ? ": fictional, not evidence of accuracy" : "").append("\n\n");
        out.append("- Mode: ").append(mode).append(" (").append(mode.description()).append(")\n");
        out.append("- Model: ").append(model).append(" on host ").append(host == null ? "n/a" : host).append(", ").append(date)
                .append(", split ").append(split).append('\n');
        for (AnswerKeys.Loaded k : keys) {
            AnswerKey.Provenance p = k.key().provenance();
            long independent = p.labellers().stream().filter(AnswerKey.Labeller::independent).count();
            out.append(String.format(Locale.ROOT, "- Key %s (file SHA-256 %s): %s, labelled %s by %d labeller%s (%d independent), %d requirements%n",
                    k.id(), k.sha256().substring(0, 12), p.fictional() ? "fictional" : "real", p.date(), p.labellers().size(),
                    p.labellers().size() == 1 ? "" : "s", independent, k.key().requirements().size()));
            AnswerKey.SourceBundle b = p.sourceBundle();
            out.append(String.format(Locale.ROOT, "  Program %s, catalog %s; %d rule versions; %d documents%n", b.program().name(),
                    b.program().catalogYear(), b.ruleVersions().size(), b.documents().size()));
        }
        List<String> blockers = claimBlockers();
        out.append("- Accuracy claim supported: ").append(blockers.isEmpty() ? "no objection recorded" : "no, because " + String.join("; ", blockers))
                .append("\n\n");
        Ratio headline = pipeline.score().overall().get(LabMetric.SEVERE_FALSE_ASSURANCE);
        out.append("Severe false assurance, the headline: ").append(headline.counts()).append(" (").append(headline.percent())
                .append("), 95 % Wilson interval ").append(headline.interval()).append("\n\n");
        metricTable(out);
        breakdowns(out, "Pipeline", pipeline);
        if (baseline.status() == Baseline.Status.MEASURED) {
            breakdowns(out, "Generic baseline", baseline.measured());
        }
        usage(out);
        disagreements(out);
        out.append("\nRESULT: ").append(passed() ? "pass" : "FAIL").append('\n');
        breaches.forEach(b -> out.append("- ").append(b).append('\n'));
        return out.toString();
    }

    private void metricTable(StringBuilder out) {
        boolean compare = baseline.status() != Baseline.Status.NOT_REQUESTED;
        out.append("| Metric | Pipeline |").append(compare ? " Generic baseline |" : "").append(" Limit | Status |\n");
        out.append("|---|---|").append(compare ? "---|" : "").append("---|---|\n");
        for (LabMetric metric : LabMetric.values()) {
            Ratio ratio = pipeline.score().overall().get(metric);
            out.append("| ").append(metric.label()).append(" | ").append(cell(ratio)).append(" |");
            if (compare) {
                out.append(' ').append(baseline.status() == Baseline.Status.MEASURED ? cell(baseline.measured().score().overall().get(metric))
                        : "BLOCKED: " + baseline.reason()).append(" |");
            }
            out.append(' ').append(thresholds.describe(metric)).append(" | ")
                    .append(thresholds.status(metric, ratio).name().toLowerCase(Locale.ROOT)).append(" |\n");
        }
        out.append("| Quote verification | ").append(cell(pipeline.quoteVerification())).append(" |");
        if (compare) {
            out.append(' ').append(baseline.status() == Baseline.Status.MEASURED ? cell(baseline.measured().quoteVerification())
                    : "BLOCKED: " + baseline.reason()).append(" |");
        }
        out.append(" = 100.0 % | ").append(pipeline.quoteVerification().isComplete() ? "ok" : "FAIL").append(" |\n");
        if (baseline.status() == Baseline.Status.NOT_REQUESTED) {
            out.append("\nGeneric baseline: ").append(baseline.reason()).append('\n');
        }
        if (baseline.status() == Baseline.Status.MEASURED) {
            baseline.measured().notes().forEach(n -> out.append("\nGeneric baseline: ").append(n).append('\n'));
        }
    }

    private static String cell(Ratio ratio) {
        return ratio.denominator() == 0 ? "n/a (0 / 0)" : ratio.counts() + ", " + ratio.percent() + " " + ratio.interval();
    }

    private static void breakdowns(StringBuilder out, String system, Measured measured) {
        measured.score().breakdowns().forEach((dimension, byValue) -> {
            out.append("\n### ").append(system).append(" by ").append(dimension).append("\n\n| ").append(dimension).append(" |");
            for (LabMetric metric : LabMetric.values()) {
                out.append(' ').append(metric.label()).append(" |");
            }
            out.append("\n|---|").append("---|".repeat(LabMetric.values().length)).append('\n');
            byValue.forEach((value, metrics) -> {
                out.append("| ").append(value).append(" |");
                for (LabMetric metric : LabMetric.values()) {
                    out.append(' ').append(metrics.get(metric).counts()).append(" |");
                }
                out.append('\n');
            });
        });
    }

    private void usage(StringBuilder out) {
        out.append("\n### Model use\n\n");
        out.append(usageLine("Pipeline", pipeline.usage()));
        if (baseline.status() == Baseline.Status.MEASURED) {
            out.append(usageLine("Generic baseline", baseline.measured().usage()));
        }
        if (pipeline.usage().tokensEstimated() || (baseline.measured() != null && baseline.measured().usage().tokensEstimated())) {
            out.append("\nToken counts marked as estimates come from text length; the provider reported none.\n");
        }
    }

    private static String usageLine(String system, EvalReport.Usage usage) {
        return String.format(Locale.ROOT, "- %s: %d calls, %,d input tokens, %,d output tokens, %.1f s, cost %s%n", system, usage.calls(),
                usage.inputTokens(), usage.outputTokens(), usage.wallTime().toMillis() / 1000.0,
                usage.cost() == null ? "n/a (no price in LEXVAY_LLM_RATES)" : usage.cost().toPlainString() + " USD");
    }

    private void disagreements(StringBuilder out) {
        List<LabScorer.Row> wrong = pipeline.rows().stream().filter(r -> r.severeFalseAssurance() || !agrees(r)).toList();
        if (wrong.isEmpty()) {
            return;
        }
        out.append("\n### Disagreements with the key (pipeline)\n\n");
        for (LabScorer.Row r : wrong) {
            out.append(String.format(Locale.ROOT, "- %s %s: key %s, answered %s%s%n", r.keyId(), r.requirement().key(), r.requirement().expected(),
                    r.answer().finding() == null ? "nothing" : r.answer().finding(), r.severeFalseAssurance() ? " (severe false assurance)" : ""));
        }
    }

    private static boolean agrees(LabScorer.Row row) {
        return row.requirement().expected() == AnswerKey.Expected.NOT_APPLICABLE || row.answer().finding() == row.requirement().expected().finding();
    }

    public ObjectNode json(ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", FORMAT).put("mode", mode.name()).put("model", model).put("host", host).put("date", date.toString())
                .put("split", split.name()).put("passed", passed());
        ArrayNode blockers = root.putArray("claimBlockers");
        claimBlockers().forEach(blockers::add);
        ArrayNode keyNodes = root.putArray("keys");
        for (AnswerKeys.Loaded k : keys) {
            AnswerKey.Provenance p = k.key().provenance();
            ObjectNode node = keyNodes.addObject();
            node.put("id", k.id()).put("sha256", k.sha256()).put("fictional", p.fictional()).put("date", p.date().toString())
                    .put("requirements", k.key().requirements().size());
            ArrayNode labellers = node.putArray("labellers");
            p.labellers().forEach(l -> labellers.addObject().put("id", l.id()).put("role", l.role()).put("independent", l.independent()));
        }
        root.set("pipeline", measuredJson(mapper, pipeline));
        ObjectNode base = root.putObject("baseline");
        base.put("status", baseline.status().name());
        if (baseline.reason() != null) {
            base.put("reason", baseline.reason());
        }
        if (baseline.measured() != null) {
            base.set("measured", measuredJson(mapper, baseline.measured()));
        }
        ObjectNode limits = root.putObject("limits");
        for (LabMetric metric : LabMetric.values()) {
            limits.put(metric.key(), thresholds.describe(metric));
        }
        ArrayNode breachNodes = root.putArray("breaches");
        breaches.forEach(breachNodes::add);
        return root;
    }

    private static ObjectNode measuredJson(ObjectMapper mapper, Measured measured) {
        ObjectNode node = mapper.createObjectNode();
        node.set("metrics", metricsJson(mapper, measured.score().overall()));
        ObjectNode breakdowns = node.putObject("breakdowns");
        measured.score().breakdowns().forEach((dimension, byValue) -> {
            ObjectNode values = breakdowns.putObject(dimension);
            byValue.forEach((value, metrics) -> values.set(value, metricsJson(mapper, metrics)));
        });
        node.set("quoteVerification", ratioJson(mapper, measured.quoteVerification()));
        EvalReport.Usage u = measured.usage();
        node.putObject("usage").put("calls", u.calls()).put("inputTokens", u.inputTokens()).put("outputTokens", u.outputTokens())
                .put("tokensEstimated", u.tokensEstimated()).put("costUsd", u.cost() == null ? null : u.cost().toPlainString())
                .put("wallMillis", u.wallTime().toMillis());
        ArrayNode notes = node.putArray("notes");
        measured.notes().forEach(notes::add);
        ArrayNode rows = node.putArray("rows");
        for (LabScorer.Row r : measured.rows()) {
            rows.addObject().put("key", r.keyId()).put("requirement", r.requirement().key()).put("state", r.requirement().jurisdiction())
                    .put("requirementType", r.requirement().type().name()).put("severity", r.requirement().severity().name())
                    .put("expected", r.requirement().expected().name())
                    .put("answered", r.answer().finding() == null ? null : r.answer().finding().name())
                    .put("evidenceMatches", r.evidenceMatches()).put("severeFalseAssurance", r.severeFalseAssurance());
        }
        return node;
    }

    private static ObjectNode metricsJson(ObjectMapper mapper, Map<LabMetric, Ratio> metrics) {
        ObjectNode node = mapper.createObjectNode();
        metrics.forEach((metric, ratio) -> node.set(metric.key(), ratioJson(mapper, ratio)));
        return node;
    }

    private static ObjectNode ratioJson(ObjectMapper mapper, Ratio ratio) {
        ObjectNode node = mapper.createObjectNode();
        node.put("numerator", ratio.numerator()).put("denominator", ratio.denominator());
        if (ratio.denominator() > 0) {
            Ratio.Interval interval = ratio.wilson95();
            node.put("rate", ratio.value()).put("low", interval.low()).put("high", interval.high());
        }
        return node;
    }
}
