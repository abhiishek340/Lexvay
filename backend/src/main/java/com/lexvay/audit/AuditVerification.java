package com.lexvay.audit;

import java.time.OffsetDateTime;

/**
 * What a walk over the tenant's seals found. {@code sealed} and {@code headHash} describe the seals as stored;
 * {@code pending} entries are written but not sealed yet, so they are not covered. When {@code intact} is false,
 * {@code firstBrokenSeq} and {@code firstBrokenReason} name the first seal that does not hold; both are null otherwise.
 * Nothing sealed gives a null {@code lastSealedAt} and {@code headHash}.
 */
public record AuditVerification(long sealed, long pending, boolean intact, Long firstBrokenSeq, String firstBrokenReason,
                                OffsetDateTime lastSealedAt, String headHash) {
}
