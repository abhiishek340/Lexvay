package com.lexvay.overview;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/programs/{programId}/overview")
public class ProgramOverviewController {

    private final ProgramOverviewService service;

    public ProgramOverviewController(ProgramOverviewService service) {
        this.service = service;
    }

    @GetMapping
    ProgramOverview get(@PathVariable UUID programId) {
        return service.overview(programId);
    }
}
