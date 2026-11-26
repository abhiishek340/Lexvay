package com.lexvay.program;

import com.lexvay.rule.NumericField;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProgramFact(UUID id, UUID programId, NumericField key, BigDecimal value, UUID evidenceSpanId,
                          UUID documentId, String filename, int pageNumber, String quote, OffsetDateTime createdAt,
                          UUID createdBy, boolean current) {
}
