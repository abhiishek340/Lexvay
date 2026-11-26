package com.lexvay.rule;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/rule-versions")
public class RuleVersionController {

    private final RuleVersionService service;
    private final RuleVersionDiffService diffs;

    public RuleVersionController(RuleVersionService service, RuleVersionDiffService diffs) {
        this.service = service;
        this.diffs = diffs;
    }

    /** Long operation: requirement extraction runs as a job. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMIN')")
    RuleVersionService.CreateResult create(@Valid @RequestBody CreateRuleVersionRequest request) {
        return service.create(request);
    }

    @GetMapping
    List<RuleVersionService.RuleVersionView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    RuleVersionService.RuleVersionView get(@PathVariable UUID id) {
        return service.view(id);
    }

    @GetMapping("/{id}/requirements")
    RuleVersionService.RequirementsView requirements(@PathVariable UUID id) {
        return service.requirements(id);
    }

    /** What changed from {@code against} (default: the version this one supersedes) to this version. */
    @GetMapping("/{id}/diff")
    RuleVersionDiffService.Diff diff(@PathVariable UUID id, @RequestParam(required = false) UUID against) {
        return diffs.diff(id, against);
    }

    @PutMapping("/{id}/requirements")
    @PreAuthorize("hasAnyRole('REVIEWER', 'SIGNER', 'ADMIN')")
    RuleVersionService.RequirementsView replace(@PathVariable UUID id, @RequestBody JsonNode body) {
        return service.replaceRequirements(id, body);
    }

    @PostMapping("/{id}/requirements/approve")
    @PreAuthorize("hasAnyRole('REVIEWER', 'SIGNER', 'ADMIN')")
    RuleVersionService.RuleVersionView approve(@PathVariable UUID id) {
        return service.approveRequirements(id);
    }
}
