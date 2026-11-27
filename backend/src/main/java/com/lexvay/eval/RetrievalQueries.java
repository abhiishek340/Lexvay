package com.lexvay.eval;

import java.util.List;

/** Shape of eval/retrieval/queries.yml: requirement-style questions and the page that answers each one. */
public record RetrievalQueries(boolean fictional, List<Query> queries) {

    /**
     * @param title      the requirement's title, as the matcher searches it
     * @param text       the requirement's text
     * @param file       the PDF holding the evidence
     * @param page       the page of that PDF
     * @param contains   a substring of the passage that carries the evidence (whitespace-insensitive)
     * @param lexicalGap the document says it in other words than the requirement does; reported apart and never thresholded
     */
    public record Query(String id, String title, String text, String file, int page, String contains, Boolean lexicalGap) {

        /** The key is left out of the file for an ordinary query. */
        public Query {
            lexicalGap = Boolean.TRUE.equals(lexicalGap);
        }
    }
}
