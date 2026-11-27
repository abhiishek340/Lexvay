package com.lexvay.auth;

import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The application's password encoder (D-400). New hashes are Argon2id and carry the prefix {@code {argon2}}. Hashes
 * made before that are {@code {bcrypt}}, or have no prefix at all, and still verify; they are replaced at the next
 * successful sign-in (D-401). Nothing else verifies, so a value such as {@code {noop}secret} in the database is not a
 * password.
 */
public final class PasswordEncoders {

    public static final String ARGON2 = "argon2";
    public static final String BCRYPT = "bcrypt";

    /**
     * Argon2id with 32 MiB, 4 passes, one lane: more memory and more passes than every combination OWASP lists
     * (46 MiB with 1 pass, 19 MiB with 2, 12 MiB with 3, and so on), about 70 ms per hash on the development machine.
     */
    static final int SALT_BYTES = 16;
    static final int HASH_BYTES = 32;
    static final int PARALLELISM = 1;
    static final int MEMORY_KIB = 32 * 1024;
    static final int ITERATIONS = 4;

    private PasswordEncoders() {
    }

    public static PasswordEncoder application() {
        var bcrypt = new BCryptPasswordEncoder();
        var delegating = new DelegatingPasswordEncoder(ARGON2, Map.of(
                ARGON2, new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS),
                BCRYPT, bcrypt));
        // Hashes written before the prefix existed have none; they are bcrypt.
        delegating.setDefaultPasswordEncoderForMatches(bcrypt);
        return delegating;
    }

    /** The algorithm a stored hash was made with: the id in its prefix, or bcrypt when there is none. */
    public static String algorithmOf(String hash) {
        int end = hash.indexOf('}');
        return hash.startsWith("{") && end > 0 ? hash.substring(1, end) : BCRYPT;
    }
}
