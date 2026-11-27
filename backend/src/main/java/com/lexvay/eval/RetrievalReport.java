package com.lexvay.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What one retrieval measurement found: the baseline ranker, BM25 on the requirement's words, and BM25 with the
 * requirement's search terms (F7c) over the same queries, as a console table and as docs/RETRIEVAL.md.
 */
public record RetrievalReport(int documents, List<RetrievalScorer.Result> baselineResults, List<RetrievalScorer.Result> bm25Results,
                              List<RetrievalScorer.Result> termsResults, RetrievalScorer.Score baseline, RetrievalScorer.Score bm25,
                              RetrievalScorer.Score terms, Thresholds.Retrieval limits, List<String> breaches) {

    public static RetrievalReport of(int documents, List<RetrievalScorer.Result> baselineResults, List<RetrievalScorer.Result> bm25Results,
                                     List<RetrievalScorer.Result> termsResults, Thresholds.Retrieval limits) {
        RetrievalScorer.Score baseline = RetrievalScorer.score(baselineResults);
        RetrievalScorer.Score bm25 = RetrievalScorer.score(bm25Results);
        RetrievalScorer.Score terms = RetrievalScorer.score(termsResults);
        List<String> breaches = new ArrayList<>(Thresholds.retrievalBreaches(limits, bm25.counted(), baseline.counted()));
        breaches.addAll(Thresholds.termsBreaches(limits, terms, bm25.counted()));
        return new RetrievalReport(documents, baselineResults, bm25Results, termsResults, baseline, bm25, terms, limits, List.copyOf(breaches));
    }

    public boolean passed() {
        return breaches.isEmpty();
    }

    public String render() {
        RetrievalScorer.Metrics base = baseline.counted();
        RetrievalScorer.Metrics now = bm25.counted();
        RetrievalScorer.Metrics plus = terms.counted();
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Retrieval (fictional corpus of %d documents; %d queries count, %d lexical gaps are reported apart)%n",
                documents, base.recallAtDeep().denominator(), baseline.gap().recallAtDeep().denominator()));
        out.append(String.format(Locale.ROOT, "%-28s %20s %20s %20s   %-30s %s%n", "Metric", "ts_rank (baseline)", "BM25", "BM25 + terms", "Limit", "Status"));
        row(out, "Recall@" + RetrievalScorer.DEEP, base.recallAtDeep(), now.recallAtDeep(), plus.recallAtDeep(),
                ">= " + Thresholds.percent(limits.minRecallAtDeep()) + ", not below baseline",
                Thresholds.meetsMinimum(now.recallAtDeep(), limits.minRecallAtDeep()) && notBelow(now.recallAtDeep(), base.recallAtDeep())
                        && Thresholds.meetsMinimum(plus.recallAtDeep(), limits.minRecallAtDeep()) && notBelow(plus.recallAtDeep(), now.recallAtDeep()));
        row(out, "Recall@" + RetrievalScorer.SHALLOW, base.recallAtShallow(), now.recallAtShallow(), plus.recallAtShallow(), "not below baseline",
                notBelow(now.recallAtShallow(), base.recallAtShallow()) && notBelow(plus.recallAtShallow(), now.recallAtShallow()));
        out.append(String.format(Locale.ROOT, "%-28s %20.3f %20.3f %20.3f   %-30s %s%n", "Mean reciprocal rank", base.meanReciprocalRank(),
                now.meanReciprocalRank(), plus.meanReciprocalRank(), "-", "-"));
        row(out, "Excerpt coverage", base.excerptCoverage(), now.excerptCoverage(), plus.excerptCoverage(),
                "= " + Thresholds.percent(limits.minExcerptCoverage()),
                Thresholds.meetsMinimum(now.excerptCoverage(), limits.minExcerptCoverage())
                        && Thresholds.meetsMinimum(plus.excerptCoverage(), limits.minExcerptCoverage()));
        RetrievalScorer.Metrics gap = terms.gap();
        out.append(String.format(Locale.ROOT, "%nLexical gaps: recall@%d %s with ts_rank, %s with BM25, %s with BM25 + terms (limit >= %s, %s); recall@%d %s, %s and %s%n",
                RetrievalScorer.DEEP, baseline.gap().recallAtDeep().counts(), bm25.gap().recallAtDeep().counts(), gap.recallAtDeep().counts(),
                Thresholds.percent(limits.minGapRecallAtDeep()), Thresholds.meetsMinimum(gap.recallAtDeep(), limits.minGapRecallAtDeep()) ? "ok" : "FAIL",
                RetrievalScorer.SHALLOW, baseline.gap().recallAtShallow().counts(), bm25.gap().recallAtShallow().counts(), gap.recallAtShallow().counts()));
        misses(out, "BM25", bm25);
        misses(out, "BM25 + terms", terms);
        out.append('\n').append(passed() ? "RETRIEVAL: pass" : "RETRIEVAL: FAIL").append('\n');
        breaches.forEach(b -> out.append("  - ").append(b).append('\n'));
        return out.toString();
    }

    /** The content of docs/RETRIEVAL.md. It has no date, so a run that changes nothing changes no line. */
    public String markdown() {
        StringBuilder out = new StringBuilder();
        out.append("# Retrieval quality\n\n");
        out.append("Written by `make eval` (`RetrievalService`); do not edit by hand. Fictional data: the corpus is the demo seed plus the syllabi in\n");
        out.append("`eval/retrieval/docs`, and the questions are in `eval/retrieval/queries.yml`. A query is recalled when its expected page is among\n");
        out.append("the first eight pages the ranker returns for the requirement's title and text. \"BM25 + terms\" also searches the requirement's\n");
        out.append("search terms (F7c), which the file holds per query, hand-written before the expected page was looked at. Excerpt coverage is the\n");
        out.append("share of recalled queries whose expected evidence is inside the excerpt that would be shown to the model (D-174).\n\n");
        out.append(String.format(Locale.ROOT, "Corpus: %d documents. Queries: %d counted and %d lexical gaps.%n%n", documents,
                baseline.counted().recallAtDeep().denominator(), baseline.gap().recallAtDeep().denominator()));
        out.append("## Queries that count toward the limits\n\n");
        table(out, baseline.counted(), bm25.counted(), terms.counted());
        out.append("\nLimits (`eval/thresholds.yml`): recall@8 at least ").append(Thresholds.percent(limits.minRecallAtDeep()))
                .append(" and, for BM25, not below the baseline and, for BM25 + terms, not below BM25; recall@3 likewise; excerpt coverage ")
                .append(Thresholds.percent(limits.minExcerptCoverage())).append(".\n\n");
        out.append("## Lexical gaps\n\n");
        out.append("The document says it in other words than the requirement does, so a search on the requirement's words cannot be expected to\n");
        out.append("find it. These numbers show what lexical search does not reach and what the search terms recover. Only the recall@8 of\n");
        out.append("BM25 + terms is limited: at least ").append(Thresholds.percent(limits.minGapRecallAtDeep())).append(" (4 of 6).\n\n");
        table(out, baseline.gap(), bm25.gap(), terms.gap());
        out.append("\n## Every query\n\n");
        out.append("Rank is the position of the expected page in the first eight, `-` when it is not there.\n\n");
        out.append("| Query | Kind | Expected page | ts_rank | BM25 | BM25 + terms | Excerpt covers (BM25 + terms) | Search terms |\n|---|---|---|---|---|---|---|---|\n");
        for (int i = 0; i < bm25Results.size(); i++) {
            RetrievalScorer.Result base = baselineResults.get(i);
            RetrievalScorer.Result now = bm25Results.get(i);
            RetrievalScorer.Result plus = termsResults.get(i);
            out.append(String.format(Locale.ROOT, "| %s %s | %s | `%s` p. %d | %s | %s | %s | %s | %s |%n", now.query().id(), now.query().title(),
                    now.query().lexicalGap() ? "lexical gap" : "counted", now.query().file(), now.query().page(), rank(base), rank(now), rank(plus),
                    plus.rank() == 0 ? "-" : plus.excerptCoversEvidence() ? "yes" : "no", String.join(", ", now.query().searchTerms())));
        }
        return out.toString();
    }

    private static void misses(StringBuilder out, String ranker, RetrievalScorer.Score score) {
        score.misses().stream().filter(m -> !m.lexicalGap()).forEach(m -> out.append("  ").append(ranker).append(' ').append(m.query())
                .append(": ").append(m.problem()).append('\n'));
    }

    private void table(StringBuilder out, RetrievalScorer.Metrics base, RetrievalScorer.Metrics now, RetrievalScorer.Metrics plus) {
        out.append("| Metric | ts_rank (baseline) | BM25 | BM25 + terms |\n|---|---|---|---|\n");
        mdRow(out, "Recall@" + RetrievalScorer.DEEP, base.recallAtDeep(), now.recallAtDeep(), plus.recallAtDeep());
        mdRow(out, "Recall@" + RetrievalScorer.SHALLOW, base.recallAtShallow(), now.recallAtShallow(), plus.recallAtShallow());
        out.append(String.format(Locale.ROOT, "| Mean reciprocal rank | %.3f | %.3f | %.3f |%n", base.meanReciprocalRank(), now.meanReciprocalRank(),
                plus.meanReciprocalRank()));
        mdRow(out, "Excerpt coverage", base.excerptCoverage(), now.excerptCoverage(), plus.excerptCoverage());
    }

    private static void mdRow(StringBuilder out, String name, Ratio base, Ratio now, Ratio plus) {
        out.append(String.format(Locale.ROOT, "| %s | %s (%s) | %s (%s) | %s (%s) |%n", name, base.percent(), base.counts(), now.percent(), now.counts(),
                plus.percent(), plus.counts()));
    }

    private static void row(StringBuilder out, String name, Ratio base, Ratio now, Ratio plus, String limit, boolean ok) {
        out.append(String.format(Locale.ROOT, "%-28s %20s %20s %20s   %-30s %s%n", name, base.percent() + "  " + base.counts(),
                now.percent() + "  " + now.counts(), plus.percent() + "  " + plus.counts(), limit, ok ? "ok" : "FAIL"));
    }

    private static String rank(RetrievalScorer.Result result) {
        return result.rank() == 0 ? "-" : String.valueOf(result.rank());
    }

    /** Recall never gets worse by moving from one ranker to the next. */
    private static boolean notBelow(Ratio now, Ratio before) {
        return now.denominator() > 0 && now.value() >= before.value();
    }
}
