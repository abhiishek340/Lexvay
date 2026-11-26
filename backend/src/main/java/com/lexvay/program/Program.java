package com.lexvay.program;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record Program(UUID id, String name, String licenseType, String catalogYear, List<String> jurisdictions,
                      OffsetDateTime createdAt, UUID createdBy) {
}
