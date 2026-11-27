package com.lexvay.assessment;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AssessmentUsageController {

    private final AssessmentUsageService service;

    public AssessmentUsageController(AssessmentUsageService service) {
        this.service = service;
    }

    /** Any tenant user may read it; the assessment lookup is tenant-scoped, so another tenant's id is a 404. */
    @GetMapping("/assessments/{id}/usage")
    AssessmentUsageService.UsageView usage(@PathVariable UUID id) {
        return service.usage(id);
    }
}
