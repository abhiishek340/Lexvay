package com.lexvay.document;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.MediaType;

/**
 * The accepted formats. Uploads are PDF, DOCX or TXT, detected from the file name and the leading bytes
 * ({@link #detect}); HTML is accepted only from the rule source fetcher ({@link #detectFetched}), never as an upload.
 */
public enum FileType {
    PDF(MediaType.APPLICATION_PDF),
    DOCX(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
    /** Text uploads are accepted only as valid UTF-8, and rule text is stored as UTF-8. */
    TXT(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8)),
    /**
     * A page fetched from a licensing board's site. It is served as plain text, never as {@code text/html}: a third
     * party's markup must not render on the application's own origin.
     */
    HTML(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));

    private final MediaType mediaType;

    FileType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    /** The media type the original file is served with. */
    public MediaType mediaType() {
        return mediaType;
    }

    public static FileType detect(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") && startsWith(bytes, "%PDF-")) {
            return PDF;
        }
        if (name.endsWith(".docx") && bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4) {
            return DOCX;
        }
        if (name.endsWith(".txt") && isUtf8Text(bytes)) {
            return TXT;
        }
        throw new IllegalArgumentException("Unsupported file type. Upload a text-based PDF, a DOCX or a UTF-8 TXT file");
    }

    /**
     * The format of a fetched page or file, from its content type and leading bytes. Magic bytes win for PDF and
     * DOCX, because servers often label a download {@code application/octet-stream}; a page is HTML when it says
     * so or starts like HTML.
     */
    public static FileType detectFetched(String contentType, byte[] bytes) {
        String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (startsWith(bytes, "%PDF-")) {
            return PDF;
        }
        if (bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4) {
            return DOCX;
        }
        if (mediaType.equals("text/html") || mediaType.equals("application/xhtml+xml") || looksLikeHtml(bytes)) {
            return HTML;
        }
        if (mediaType.equals("text/plain") && isUtf8Text(bytes)) {
            return TXT;
        }
        throw new IllegalArgumentException("Unsupported content type"
                + (mediaType.isEmpty() ? "" : " " + mediaType) + ": expected a PDF, a DOCX, an HTML page or UTF-8 text");
    }

    /** The extension of a stored file of this type. */
    public String extension() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FileType fromFilename(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return PDF;
        }
        if (name.endsWith(".docx")) {
            return DOCX;
        }
        if (name.endsWith(".txt")) {
            return TXT;
        }
        if (name.endsWith(".html")) {
            return HTML;
        }
        throw new IllegalArgumentException("Unsupported file type: " + filename);
    }

    /** A leading {@code <!doctype html} or {@code <html}, ignoring a byte order mark, white space and case. */
    private static boolean looksLikeHtml(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.UTF_8)
                .stripLeading().replaceFirst("^\\uFEFF", "").stripLeading().toLowerCase(Locale.ROOT);
        return head.startsWith("<!doctype html") || head.startsWith("<html");
    }

    private static boolean startsWith(byte[] bytes, String prefix) {
        byte[] p = prefix.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < p.length) {
            return false;
        }
        for (int i = 0; i < p.length; i++) {
            if (bytes[i] != p[i]) {
                return false;
            }
        }
        return true;
    }

    static boolean isUtf8Text(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
