package com.lexvay.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.audit.AuditCanonical.Entry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The canonical form is a contract with auditors (docs/OPERATIONS.md): the expected hashes below were computed outside
 * this code, with Python's hashlib over the same fields, so a change to the form fails here before it breaks a chain.
 */
class AuditCanonicalTest {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID ENTRY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID RECORD = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void theGenesisHashIsSixtyFourZeros() {
        assertThat(AuditCanonical.GENESIS_HASH).isEqualTo("0".repeat(64));
    }

    @Test
    void aFullEntryHashesToTheValueComputedOutsideTheCode() {
        Entry entry = new Entry(ENTRY, USER, "MAPPING_REVIEWED", "mapping", RECORD, "{\"decision\": \"ACCEPTED\"}",
                Instant.parse("2026-11-27T10:15:30.120Z"));

        assertThat(AuditCanonical.rowHash(TENANT, 7, entry, AuditCanonical.GENESIS_HASH))
                .isEqualTo("cbd86df1e27de666207b91089b4e5c2cbeb1b74a638bbc11348f97854ea53eb6");
    }

    @Test
    void aMissingActorAndRecordAreEmptyFieldsAndTheTimeAlwaysHasSixFractionDigits() {
        Entry entry = new Entry(ENTRY, null, "LOGIN_SUCCEEDED", "app_user", null, "{}", Instant.parse("2026-11-27T10:15:30Z"));

        assertThat(AuditCanonical.rowHash(TENANT, 1, entry, AuditCanonical.GENESIS_HASH))
                .isEqualTo("d976c93771bc623f3e6c2f03d63e24b5f1acc8085a56853bc1b54c5a2afa2f17");
    }

    @Test
    void everyFieldChangesTheHash() {
        Entry entry = new Entry(ENTRY, USER, "A", "t", RECORD, "{}", Instant.parse("2026-11-27T10:15:30.000001Z"));
        String base = AuditCanonical.rowHash(TENANT, 1, entry, AuditCanonical.GENESIS_HASH);

        assertThat(AuditCanonical.rowHash(UUID.randomUUID(), 1, entry, AuditCanonical.GENESIS_HASH)).isNotEqualTo(base);
        assertThat(AuditCanonical.rowHash(TENANT, 2, entry, AuditCanonical.GENESIS_HASH)).isNotEqualTo(base);
        assertThat(AuditCanonical.rowHash(TENANT, 1, entry, "1".repeat(64))).isNotEqualTo(base);
        assertThat(AuditCanonical.rowHash(TENANT, 1, new Entry(UUID.randomUUID(), USER, "A", "t", RECORD, "{}", entry.createdAt()),
                AuditCanonical.GENESIS_HASH)).isNotEqualTo(base);
        assertThat(AuditCanonical.rowHash(TENANT, 1, new Entry(ENTRY, null, "A", "t", RECORD, "{}", entry.createdAt()),
                AuditCanonical.GENESIS_HASH)).isNotEqualTo(base);
        assertThat(AuditCanonical.rowHash(TENANT, 1, new Entry(ENTRY, USER, "B", "t", RECORD, "{}", entry.createdAt()),
                AuditCanonical.GENESIS_HASH)).isNotEqualTo(base);
        assertThat(AuditCanonical.rowHash(TENANT, 1, new Entry(ENTRY, USER, "A", "u", RECORD, "{}", entry.createdAt()),
                AuditCanonical.GENESIS_HASH)).isNotEqualTo(base);
        assertThat(AuditCanonical.rowHash(TENANT, 1, new Entry(ENTRY, USER, "A", "t", null, "{}", entry.createdAt()),
                AuditCanonical.GENESIS_HASH)).isNotEqualTo(base);
        assertThat(AuditCanonical.rowHash(TENANT, 1, new Entry(ENTRY, USER, "A", "t", RECORD, "{\"a\": 1}", entry.createdAt()),
                AuditCanonical.GENESIS_HASH)).isNotEqualTo(base);
        assertThat(AuditCanonical.rowHash(TENANT, 1, new Entry(ENTRY, USER, "A", "t", RECORD, "{}", entry.createdAt().plusNanos(1000)),
                AuditCanonical.GENESIS_HASH)).as("one microsecond").isNotEqualTo(base);
    }
}
