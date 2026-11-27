package com.lexvay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** D-400: new hashes are Argon2id; bcrypt hashes, with or without a prefix, still verify and are due for replacement. */
class PasswordEncodersTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    private final PasswordEncoder encoder = PasswordEncoders.application();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Test
    void newHashesAreArgon2idWithThePrefixAndTheDocumentedCost() {
        String hash = encoder.encode(PASSWORD);

        assertThat(hash).startsWith("{argon2}$argon2id$v=19$m=32768,t=4,p=1$").doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        assertThat(encoder.matches(PASSWORD + "x", hash)).isFalse();
        assertThat(encoder.encode(PASSWORD)).as("every hash has its own salt").isNotEqualTo(hash);
        assertThat(encoder.upgradeEncoding(hash)).as("nothing to replace").isFalse();
    }

    @Test
    void aPrefixedBcryptHashStillVerifiesAndIsDueForReplacement() {
        String hash = "{bcrypt}" + bcrypt.encode(PASSWORD);

        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        assertThat(encoder.matches("another-password", hash)).isFalse();
        assertThat(encoder.upgradeEncoding(hash)).isTrue();
    }

    @Test
    void aBcryptHashWithoutAPrefixStillVerifiesAndIsDueForReplacement() {
        String hash = bcrypt.encode(PASSWORD);

        assertThat(hash).doesNotStartWith("{");
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        assertThat(encoder.matches("another-password", hash)).isFalse();
        assertThat(encoder.upgradeEncoding(hash)).isTrue();
    }

    @Test
    void anArgon2HashMadeWithOtherCostIsDueForReplacement() {
        String other = "{argon2}" + new Argon2PasswordEncoder(16, 32, 1, 16 * 1024, 2).encode(PASSWORD);

        assertThat(encoder.matches(PASSWORD, other)).isTrue();
        assertThat(encoder.upgradeEncoding(other)).isTrue();
    }

    @Test
    void noPasswordIsLongEnoughToBeRefused() {
        for (String password : new String[] {"p".repeat(73), "p".repeat(128), "é".repeat(128), "😀".repeat(128), "p".repeat(2000)}) {
            String hash = encoder.encode(password);
            assertThat(encoder.matches(password, hash)).as("%d characters", password.length()).isTrue();
            assertThat(encoder.matches(password + "q", hash))
                    .as("a character past byte 72 counts: there is no cut").isFalse();
        }
    }

    @Test
    void onlyArgon2AndBcryptHashesAreAccepted() {
        assertThat(encoder.matches(PASSWORD, "{noop}" + PASSWORD)).isFalse();
        assertThat(encoder.matches(PASSWORD, "{sha256}" + PASSWORD)).isFalse();
        assertThat(encoder.matches(PASSWORD, PASSWORD)).as("a plain text value is not a hash").isFalse();
        assertThat(encoder.matches(PASSWORD, null)).as("an account without a password").isFalse();
        assertThat(encoder.matches(PASSWORD, "")).isFalse();
    }

    @Test
    void theAlgorithmIsReadFromThePrefixAndIsBcryptWithoutOne() {
        assertThat(PasswordEncoders.algorithmOf(encoder.encode(PASSWORD))).isEqualTo("argon2");
        assertThat(PasswordEncoders.algorithmOf("{bcrypt}" + bcrypt.encode(PASSWORD))).isEqualTo("bcrypt");
        assertThat(PasswordEncoders.algorithmOf(bcrypt.encode(PASSWORD))).isEqualTo("bcrypt");
    }
}
