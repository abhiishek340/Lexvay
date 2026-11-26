package com.lexvay.report;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    public record ReportStatusView(UUID id, UUID determinationId, ReportStatus status) {
    }

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping("/determinations/{id}/report")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ReportService.RequestResult request(@PathVariable UUID id) {
        return service.request(id);
    }

    /** The PDF when ready; otherwise the status as JSON with 202 (D-069). */
    @GetMapping("/reports/{id}")
    ResponseEntity<?> get(@PathVariable UUID id) {
        Report report = service.get(id);
        if (report.status() != ReportStatus.READY) {
            return ResponseEntity.status(report.status() == ReportStatus.FAILED ? HttpStatus.OK : HttpStatus.ACCEPTED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReportStatusView(report.id(), report.determinationId(), report.status()));
        }
        byte[] pdf = service.bytes(report);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lexvay-evidence-report-" + id + ".pdf\"")
                .body(pdf);
    }
}
