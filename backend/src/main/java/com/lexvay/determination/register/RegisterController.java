package com.lexvay.determination.register;

import com.lexvay.common.CsvWriter;
import com.lexvay.determination.Outcome;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The determination register (F8). Every role of the tenant may read it; row-level security scopes it. */
@RestController
@RequestMapping("/api/v1")
@Validated
public class RegisterController {

    private final RegisterService service;

    public RegisterController(RegisterService service) {
        this.service = service;
    }

    @GetMapping("/determinations/register")
    List<RegisterRow> register(@RequestParam(required = false) UUID programId,
                               @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{2}$") String jurisdiction,
                               @RequestParam(required = false) Outcome outcome,
                               @RequestParam(defaultValue = "true") boolean current) {
        return service.register(new RegisterFilter(programId, jurisdiction, outcome, current));
    }

    /** The same rows as CSV, built in memory (the register is bounded by programs times jurisdictions). */
    @GetMapping("/determinations/register.csv")
    ResponseEntity<byte[]> registerCsv(@RequestParam(required = false) UUID programId,
                                       @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{2}$") String jurisdiction,
                                       @RequestParam(required = false) Outcome outcome,
                                       @RequestParam(defaultValue = "true") boolean current) {
        byte[] csv = service.exportCsv(new RegisterFilter(programId, jurisdiction, outcome, current));
        return ResponseEntity.ok()
                .contentType(CsvWriter.TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, CsvWriter.attachment("lexvay-determination-register"))
                .body(csv);
    }

    @GetMapping("/programs/{programId}/determination-lists")
    DeterminationLists lists(@PathVariable UUID programId) {
        return service.lists(programId);
    }
}
