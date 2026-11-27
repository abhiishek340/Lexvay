package com.lexvay.coverage;

import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only and open to every signed-in role. */
@RestController
@RequestMapping("/api/v1/programs/{programId}/coverage")
@Validated
public class CoverageController {

    private final CoverageService service;

    public CoverageController(CoverageService service) {
        this.service = service;
    }

    @GetMapping
    CoverageMatrix get(@PathVariable UUID programId, @RequestParam @Pattern(regexp = "^[A-Z]{2}$") String jurisdiction) {
        return service.matrix(programId, jurisdiction);
    }
}
