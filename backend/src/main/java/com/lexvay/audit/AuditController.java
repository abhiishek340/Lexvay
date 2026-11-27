package com.lexvay.audit;

import com.lexvay.common.CsvWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class AuditController {

    private final AuditRepository repository;
    private final AuditExportService export;

    public AuditController(AuditRepository repository, AuditExportService export) {
        this.repository = repository;
        this.export = export;
    }

    /**
     * Newest first. Tenant scoping comes from row-level security, not from this query. Every filter is
     * optional; {@code from} and {@code to} are ISO-8601 instants and both ends are inclusive.
     */
    @GetMapping("/audit")
    List<AuditEntry> list(AuditFilter filter,
                          @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
                          @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return repository.newestFirst(filter, limit, offset);
    }

    /** The same entries and filters as a file, streamed and without a page limit. Administrators only. */
    @GetMapping("/audit.csv")
    @PreAuthorize("hasRole('ADMIN')")
    void exportCsv(AuditFilter filter, HttpServletResponse response) throws IOException {
        export.export(filter, () -> {
            try {
                response.setContentType(CsvWriter.TEXT_CSV.toString());
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, CsvWriter.attachment("lexvay-audit"));
                return new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
