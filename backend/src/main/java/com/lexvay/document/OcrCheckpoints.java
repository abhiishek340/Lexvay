package com.lexvay.document;

import java.util.Optional;

/**
 * The OCR results of one document that survive a failed ingest, so a retry does not recognise the same pages again
 * (FIX-6). Pages are numbered from 1. A checkpoint holds what the engine returned and never whether that was enough
 * to count as read: {@link TextExtractor} decides it from the text each time.
 */
public interface OcrCheckpoints {

    /** Finds nothing and saves nothing: rule texts, rule sources and every caller without a document. */
    OcrCheckpoints NONE = new OcrCheckpoints() {
        @Override
        public Optional<OcrPage> find(int pageNumber, int renderDpi) {
            return Optional.empty();
        }

        @Override
        public void save(int pageNumber, OcrPage page, int renderDpi) {
        }
    };

    /** The result stored for this page, or empty when there is none or it was taken at another DPI. */
    Optional<OcrPage> find(int pageNumber, int renderDpi);

    /** Keeps the result of a page that was just recognised, durably, before the next page is read. */
    void save(int pageNumber, OcrPage page, int renderDpi);
}
