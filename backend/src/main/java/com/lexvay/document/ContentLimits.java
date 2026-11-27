package com.lexvay.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipInputStream;

/**
 * Limits on what an uploaded file may expand to (D-095). The multipart size limit bounds the bytes that are
 * sent; these bound the work done on them. A DOCX is a ZIP archive, so it is inflated here, counting the bytes
 * that actually come out rather than trusting the sizes the archive declares.
 */
public final class ContentLimits {

    public static final long MAX_DOCX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;
    public static final int MAX_DOCX_EXPANSION_RATIO = 100;
    public static final int MAX_PDF_PAGES = 500;

    private ContentLimits() {
    }

    /** Thrown when a file is within the upload size limit but expands beyond what the pipeline accepts. */
    public static final class ContentLimitException extends IllegalArgumentException {
        ContentLimitException(String message) {
            super(message);
        }
    }

    public static void requireSafeDocx(byte[] docx) {
        requireSafeDocx(docx, MAX_DOCX_UNCOMPRESSED_BYTES, MAX_DOCX_EXPANSION_RATIO);
    }

    /**
     * Rejects an archive that inflates to more than {@code maxUncompressedBytes} in total, or to more than
     * {@code maxRatio} times its own size. Inflation stops at the first byte past the limit, so a bomb costs
     * at most that many bytes of work. An archive that cannot be read at all is left to the parser, which
     * reports it as unparsable.
     */
    static void requireSafeDocx(byte[] docx, long maxUncompressedBytes, int maxRatio) {
        long ratioLimit = (long) maxRatio * docx.length;
        long limit = Math.min(maxUncompressedBytes, ratioLimit);
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            while (zip.getNextEntry() != null) {
                for (int read = zip.read(buffer); read > 0; read = zip.read(buffer)) {
                    total += read;
                    if (total > limit) {
                        throw total > maxUncompressedBytes
                                ? new ContentLimitException("DOCX expands to more than " + maxUncompressedBytes / (1024 * 1024) + " MB")
                                : new ContentLimitException("DOCX expands to more than " + maxRatio + " times its size");
                    }
                }
            }
        } catch (IOException unreadable) {
            // Not a readable archive: the parser fails it with a clearer message.
        }
    }

    public static void requirePdfPages(int pages) {
        if (pages > MAX_PDF_PAGES) {
            throw new ContentLimitException("PDF has " + pages + " pages; the limit is " + MAX_PDF_PAGES);
        }
    }
}
