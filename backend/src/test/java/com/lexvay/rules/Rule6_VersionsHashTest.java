package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.determination.VersionsHash;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Product rule 6: the versions hash is deterministic, order-independent and sensitive to every id. */
class Rule6_VersionsHashTest {

    @Test
    void deterministicAndOrderIndependent() {
        UUID rule = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String h1 = VersionsHash.compute(rule, List.of(a, b));
        String h2 = VersionsHash.compute(rule, List.of(b, a));
        assertThat(h1).isEqualTo(h2).matches("^[0-9a-f]{64}$");
        assertThat(VersionsHash.compute(UUID.randomUUID(), List.of(a, b))).as("rule version matters").isNotEqualTo(h1);
        assertThat(VersionsHash.compute(rule, List.of(a))).as("every course version matters").isNotEqualTo(h1);
        assertThat(VersionsHash.compute(rule, List.of())).isNotEqualTo(h1);
    }
}
