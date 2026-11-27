package com.lexvay.assessment;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class AssessmentController {

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    /** Any tenant user may start an assessment; it only proposes, nobody is bound by it. */
    @PostMapping("/programs/{programId}/assessments")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AssessmentService.StartResult start(@PathVariable UUID programId) {
        return service.start(programId);
    }

    @GetMapping("/programs/{programId}/assessments")
    List<Assessment> list(@PathVariable UUID programId) {
        return service.listForProgram(programId);
    }

    @GetMapping("/assessments/{id}")
    AssessmentService.AssessmentView get(@PathVariable UUID id) {
        return service.view(id);
    }

    @GetMapping("/assessments/{id}/mappings")
    AssessmentService.MappingsView mappings(@PathVariable UUID id, @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{2}$") String jurisdiction) {
        return service.mappings(id, jurisdiction);
    }
}
