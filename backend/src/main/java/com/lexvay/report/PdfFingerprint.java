package com.lexvay.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * The SHA-256 of a PDF file, read once as a stream: the bytes pass through the digest in small buffers and are never
 * held whole (F14). A body that does not start with {@code %PDF-} is refused before it is hashed further.
 */
final class PdfFingerprint {

    static final String NOT_A_PDF = "Not a PDF file";

    private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int BUFFER_BYTES = 64 * 1024;

    private PdfFingerprint() {
    }

    /** Hex SHA-256 of the stream's bytes; {@link IllegalArgumentException} ({@value #NOT_A_PDF}) when it is not a PDF. */
    static String of(InputStream in) {
        MessageDigest digest = sha256();
        try (in) {
            byte[] head = in.readNBytes(MAGIC.length);
            if (!Arrays.equals(head, MAGIC)) {
                throw new IllegalArgumentException(NOT_A_PDF);
            }
            digest.update(head);
            byte[] buffer = new byte[BUFFER_BYTES];
            for (int n = in.read(buffer); n != -1; n = in.read(buffer)) {
                digest.update(buffer, 0, n);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the file", e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
