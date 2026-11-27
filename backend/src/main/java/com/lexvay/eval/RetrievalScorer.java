package com.lexvay.eval;

import com.lexvay.document.QuoteVerifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Scores a ranker's shortlists against the expected pages of eval/retrieval/queries.yml (ROADMAP F7). Pure: no database,
 * no model, no clock. Queries marked as lexical gaps are scored in a group of their own.
 */
public final class RetrievalScorer {

    public static final int DEEP = 8;
    public static final int SHALLOW = 3;

    /** A shortlisted page and the excerpt of it that would be shown to the model. */
    public record Page(String file, int page, String excerpt) {
    }

    /** One query and the shortlist the ranker returned for it, best first. */
    public record Result(RetrievalQueries.Query query, List<Page> shortlist) {

        /** 1-based position of the expected page within the first {@link #DEEP}, or 0 when it is not there. */
        int rank() {
            List<Page> deep = shortlist.subList(0, Math.min(DEEP, shortlist.size()));
            for (int i = 0; i < deep.size(); i++) {
                if (deep.get(i).file().equals(query.file()) && deep.get(i).page() == query.page()) {
                    return i + 1;
                }
            }
            return 0;
        }

        boolean excerptCoversEvidence() {
            Page expected = shortlist.get(rank() - 1);
            return QuoteVerifier.normalizeWhitespace(expected.excerpt()).contains(QuoteVerifier.normalizeWhitespace(query.contains()));
        }
    }

    /**
     * @param recallAtDeep     queries whose expected page is in the top eight, out of all queries of the group
     * @param recallAtShallow  the same for the top three
     * @param meanReciprocalRank mean of 1 / rank of the expected page, 0 when it is not in the top eight
     * @param excerptCoverage  among the recalled queries, those whose expected substring is inside the excerpt shown to the model
     */
    public record Metrics(Ratio recallAtDeep, Ratio recallAtShallow, double meanReciprocalRank, Ratio excerptCoverage) {
    }

    /** A query that was not recalled or whose evidence the excerpt left out, for the report. */
    public record Miss(String query, String problem) {
    }

    /** @param counted the queries that count toward thresholds; @param gap the lexical gaps, which never do */
    public record Score(Metrics counted, Metrics gap, List<Miss> misses) {
    }

    private RetrievalScorer() {
    }

    public static Score score(List<Result> results) {
        List<Result> counted = results.stream().filter(r -> !r.query().lexicalGap()).toList();
        List<Result> gap = results.stream().filter(r -> r.query().lexicalGap()).toList();
        List<Miss> misses = new ArrayList<>();
        for (Result r : results) {
            int rank = r.rank();
            if (rank == 0) {
                misses.add(new Miss(r.query().id(), "expected " + r.query().file() + " page " + r.query().page() + " is not in the top " + DEEP));
            } else if (!r.excerptCoversEvidence()) {
                misses.add(new Miss(r.query().id(), "excerpt of " + r.query().file() + " page " + r.query().page() + " leaves out the evidence"));
            }
        }
        return new Score(metrics(counted), metrics(gap), misses);
    }

    private static Metrics metrics(List<Result> results) {
        int deep = 0;
        int shallow = 0;
        int covered = 0;
        double reciprocalRanks = 0;
        for (Result r : results) {
            int rank = r.rank();
            if (rank == 0) {
                continue;
            }
            deep++;
            shallow += rank <= SHALLOW ? 1 : 0;
            covered += r.excerptCoversEvidence() ? 1 : 0;
            reciprocalRanks += 1.0 / rank;
        }
        return new Metrics(new Ratio(deep, results.size()), new Ratio(shallow, results.size()),
                results.isEmpty() ? 0 : reciprocalRanks / results.size(), new Ratio(covered, deep));
    }
}
