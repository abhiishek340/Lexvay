package com.lexvay.rule.source;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A web page or PDF where a licensing board publishes its rules, watched for changes. {@code validators} are those of
 * the last fetch whose text became the baseline {@code lastContentHash}.
 */
public record RuleSource(UUID id, UUID jurisdictionId, String jurisdictionCode, String licenseType, String label,
                         String url, int checkIntervalHours, boolean active, OffsetDateTime lastCheckedAt,
                         String lastContentHash, OffsetDateTime createdAt, SourceGovernance governance, Validators validators) {
}
