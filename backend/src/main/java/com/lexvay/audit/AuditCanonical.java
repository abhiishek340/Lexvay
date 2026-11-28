package com.lexvay.audit;

import com.lexvay.document.DocumentService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The bytes an audit entry is sealed with (D-531). Anyone with the entry can recompute its hash, so the form is part
 * of the product's contract and is written out in docs/OPERATIONS.md; changing it would break every existing chain.
 *
 * <p>Fields, in this order, joined with U+001F: tenant id, seq, entry id, user id (empty for the system), action,
 * record type, record id (empty when there is none), detail as PostgreSQL's {@code jsonb::text}, which is
 * deterministic, creation time as UTC ISO-8601 with exactly six fractional digits, and the previous row hash.
 * The hash is the lower-case hex SHA-256 of the UTF-8 bytes.
 */
final class AuditCanonical {

    /** The {@code prev_hash} of a tenant's first seal. */
    static final String GENESIS_HASH = "0".repeat(64);

    private static final char SEPARATOR = '\u001f';
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
            .withZone(ZoneOffset.UTC);

    private AuditCanonical() {
    }

    /** What is stored of one audit entry, as the database delivers it: {@code detail} is {@code jsonb::text}. */
    record Entry(UUID id, UUID userId, String action, String entityType, UUID entityId, String detail, Instant createdAt) {
    }

    static String rowHash(UUID tenantId, long seq, Entry entry, String prevHash) {
        String canonical = Stream.of(tenantId, seq, entry.id(), entry.userId(), entry.action(), entry.entityType(),
                        entry.entityId(), entry.detail(), TIME.format(entry.createdAt()), prevHash)
                .map(field -> field == null ? "" : field.toString())
                .collect(Collectors.joining(String.valueOf(SEPARATOR)));
        return DocumentService.sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }
}
