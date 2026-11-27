package com.lexvay.tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Setup codes (D-372): 32 random bytes from {@link SecureRandom}, shown once as unpadded base64url and kept only as
 * their SHA-256 in hex. A code is a secret, so it is never logged, audited or compared with anything but
 * {@link MessageDigest#isEqual}, which does not stop at the first differing byte.
 */
final class SetupCodes {

    static final Duration VALIDITY = Duration.ofHours(72);

    private static final int BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SetupCodes() {
    }

    static String generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The value stored and looked up: lower-case hex of the SHA-256 of the code's UTF-8 bytes. */
    static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    /** Whether {@code storedHash} is the hash of {@code code}, in time that does not depend on where they differ. */
    static boolean matches(String code, String storedHash) {
        return MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.US_ASCII), storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
