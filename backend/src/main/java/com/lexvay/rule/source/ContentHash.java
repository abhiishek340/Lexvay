package com.lexvay.rule.source;

import com.lexvay.document.DocumentService;
import com.lexvay.document.ExtractedPage;
import com.lexvay.document.QuoteVerifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The hash that decides whether a rule source changed (D-183): SHA-256 of the page text with every run of white
 * space collapsed. Different markup, line breaks or pagination around the same words give the same hash; one
 * changed word gives another. Hashing the downloaded bytes instead would flag every rendered timestamp or session
 * token on the page.
 */
final class ContentHash {

    private ContentHash() {
    }

    /** The text of the pages with every run of white space collapsed: what is hashed and what is searched. */
    static String text(List<ExtractedPage> pages) {
        return QuoteVerifier.normalizeWhitespace(pages.stream().map(ExtractedPage::text).collect(Collectors.joining("\n")));
    }

    /** The hash of text from {@link #text}. */
    static String of(String normalizedText) {
        return DocumentService.sha256(normalizedText.getBytes(StandardCharsets.UTF_8));
    }
}
