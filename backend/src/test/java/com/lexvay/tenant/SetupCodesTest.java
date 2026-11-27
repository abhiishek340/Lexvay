package com.lexvay.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SetupCodesTest {

    @Test
    void aCodeIs32RandomBytesAsUnpaddedBase64Url() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = SetupCodes.generate();
            assertThat(code).matches("[A-Za-z0-9_-]{43}");
            seen.add(code);
        }
        assertThat(seen).hasSize(200);
    }

    @Test
    void theHashIsTheSha256InLowerCaseHex() {
        assertThat(SetupCodes.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void aCodeMatchesOnlyItsOwnHashWhereverTheyDiffer() {
        String code = SetupCodes.generate();
        String hash = SetupCodes.hash(code);
        assertThat(SetupCodes.matches(code, hash)).isTrue();
        assertThat(SetupCodes.matches(code, flip(hash, 0))).as("first character differs").isFalse();
        assertThat(SetupCodes.matches(code, flip(hash, hash.length() - 1))).as("last character differs").isFalse();
        assertThat(SetupCodes.matches(code, hash.substring(1))).as("shorter").isFalse();
        assertThat(SetupCodes.matches(code, "")).isFalse();
        assertThat(SetupCodes.matches(SetupCodes.generate(), hash)).isFalse();
    }

    /** The comparison must not stop at the first difference, so it is {@code MessageDigest.isEqual}, never {@code equals}. */
    @Test
    void secretsAreComparedOnlyWithMessageDigestIsEqual() throws IOException {
        String codes = Files.readString(Path.of("src/main/java/com/lexvay/tenant/SetupCodes.java"));
        assertThat(codes).contains("MessageDigest.isEqual(");
        for (String file : new String[] {"SetupCodes", "PasswordSetupService", "SetupTokenRepository", "UserAdminService"}) {
            String source = Files.readString(Path.of("src/main/java/com/lexvay/tenant/" + file + ".java"));
            assertThat(source).as(file).doesNotContainPattern("[Hh]ash[A-Za-z()]*\\.equals\\(").doesNotContainPattern("\\.equals\\([A-Za-z.()]*[Hh]ash");
        }
    }

    private static String flip(String hex, int index) {
        char replacement = hex.charAt(index) == '0' ? '1' : '0';
        return hex.substring(0, index) + replacement + hex.substring(index + 1);
    }
}
