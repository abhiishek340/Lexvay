package com.lexvay.audit;

import com.lexvay.audit.AuditCanonical.Entry;
import java.util.Optional;
import java.util.UUID;

/**
 * Walks one tenant's seals in {@code seq} order and reports the first one that does not hold (D-534). Pure: the caller
 * supplies the seals, so the same rules are tested without a database. A seal holds when it is the next number, its
 * {@code prev_hash} is the previous seal's {@code row_hash} and its {@code row_hash} is what the stored entry hashes to.
 */
final class AuditChainCheck {

    /** A stored seal with the audit entry it covers. */
    record Sealed(long seq, String prevHash, String rowHash, Entry entry) {
    }

    /** The first seal that does not hold, and why in words an auditor can read. */
    record Broken(long seq, String reason) {
    }

    private final UUID tenantId;
    private long expectedSeq = 1;
    private String expectedPrevHash = AuditCanonical.GENESIS_HASH;

    AuditChainCheck(UUID tenantId) {
        this.tenantId = tenantId;
    }

    /** Checks the next seal; once it holds, the following one is expected to follow it. */
    Optional<Broken> next(Sealed seal) {
        if (seal.seq() != expectedSeq) {
            return Optional.of(new Broken(expectedSeq, "Seal " + expectedSeq + " is missing from the chain."));
        }
        if (!seal.prevHash().equals(expectedPrevHash)) {
            return Optional.of(new Broken(seal.seq(), "Seal " + seal.seq() + " does not follow the seal before it."));
        }
        if (!seal.rowHash().equals(AuditCanonical.rowHash(tenantId, seal.seq(), seal.entry(), seal.prevHash()))) {
            return Optional.of(new Broken(seal.seq(), "Audit entry " + seal.seq() + " no longer matches its seal."));
        }
        expectedSeq++;
        expectedPrevHash = seal.rowHash();
        return Optional.empty();
    }
}
