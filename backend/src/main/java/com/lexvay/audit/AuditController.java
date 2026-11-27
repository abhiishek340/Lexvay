package com.lexvay.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@Validated
public class AuditController {

    private final AuditRepository repository;

    public AuditController(AuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Newest first. Tenant scoping comes from row-level security, not from this query. Every filter is
     * optional; {@code from} and {@code to} are ISO-8601 instants and both ends are inclusive.
     */
    @GetMapping
    List<AuditEntry> list(@RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
                          @RequestParam(defaultValue = "0") @Min(0) int offset,
                          @RequestParam(required = false) String action,
                          @RequestParam(required = false) String entityType,
                          @RequestParam(required = false) UUID entityId,
                          @RequestParam(required = false) UUID userId,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return repository.newestFirst(new AuditFilter(action, entityType, entityId, userId, from, to), limit, offset);
    }
}
