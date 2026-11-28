package com.lexvay.rule.source;

import com.lexvay.document.FileType;
import com.lexvay.document.QuoteVerifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decides whether a fetched text can be the rule text before anything is made of it (RG-2, D-572). It looks for what
 * a log-in, challenge or block page looks like and for the strings the source is known to contain; it never tries to
 * get past one (no CAPTCHA, no log-in). Whatever it flags is recorded as SUSPECT_CONTENT: no draft is made and the
 * baseline does not move.
 */
final class ContentValidator {

    /** A body this short, in characters of extracted text, can be an error or challenge page and not a rule. */
    static final int SHORT_BODY_CHARS = 1500;

    /** The wording of the common block, challenge and error pages: the one place that lists the shapes a short body may have. */
    static final Pattern BLOCK_PAGE_SHAPES = Pattern.compile("access denied|just a moment|checking your browser|attention required"
            + "|verify you are (a )?human|are you a robot|captcha|request blocked|unusual traffic|enable javascript and cookies"
            + "|too many requests|you have been blocked", Pattern.CASE_INSENSITIVE);

    private ContentValidator() {
    }

    /**
     * The reason the content is not the source's rule text, or empty when nothing speaks against it.
     *
     * @param type the format the server's answer was read as
     * @param body the downloaded bytes, which show markup the extracted text does not
     * @param text the extracted text with white space collapsed
     */
    static Optional<String> suspicion(RuleSource source, FileType type, byte[] body, String text) {
        Optional<String> format = formatMismatch(URI.create(source.url()), type);
        if (format.isPresent()) {
            return format;
        }
        List<String> expected = source.governance().expectedIdentifiers();
        String searchable = text.toLowerCase(Locale.ROOT);
        List<String> missing = expected.stream()
                .filter(id -> !searchable.contains(QuoteVerifier.normalizeWhitespace(id).toLowerCase(Locale.ROOT))).toList();
        if (!missing.isEmpty()) {
            return Optional.of("The text does not contain " + String.join(", ", missing.stream().map(id -> "\"" + id + "\"").toList()));
        }
        if (!expected.isEmpty()) {
            return Optional.empty();
        }
        if (type == FileType.HTML && new String(body, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("<form")) {
            return Optional.of("The page has a form and none of the expected identifiers, which is what a log-in or challenge page looks like");
        }
        if (text.length() < SHORT_BODY_CHARS && BLOCK_PAGE_SHAPES.matcher(text).find()) {
            return Optional.of("The page is short and reads like a block or challenge page");
        }
        return Optional.empty();
    }

    /** A source whose address ends in a document extension is expected to serve that format; an HTML page in its place is a block page. */
    private static Optional<String> formatMismatch(URI url, FileType actual) {
        String path = FetchScope.path(url).toLowerCase(Locale.ROOT);
        FileType expected = path.endsWith(".pdf") ? FileType.PDF
                : path.endsWith(".docx") ? FileType.DOCX
                : path.endsWith(".txt") ? FileType.TXT
                : path.endsWith(".html") || path.endsWith(".htm") ? FileType.HTML : null;
        return expected == null || expected == actual ? Optional.empty()
                : Optional.of("The address ends in ." + expected.extension() + " but the server sent " + actual.name());
    }
}
