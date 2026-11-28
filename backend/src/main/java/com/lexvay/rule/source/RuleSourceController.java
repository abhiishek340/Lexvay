package com.lexvay.rule.source;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rule-sources")
@Validated
public class RuleSourceController {

    public record ActiveRequest(@NotNull Boolean active) {
    }

    private final RuleSourceService service;

    public RuleSourceController(RuleSourceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    RuleSourceService.Created create(@Valid @RequestBody CreateRuleSourceRequest request) {
        return service.create(request);
    }

    @GetMapping
    List<RuleSourceView> list() {
        return service.list();
    }

    /** Newest first. */
    @GetMapping("/{id}/checks")
    List<RuleSourceCheck> checks(@PathVariable UUID id, @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return service.checks(id, limit);
    }

    /** Long operation: the fetch runs as a job. */
    @PostMapping("/{id}/check")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    RuleSourceService.CheckQueued check(@PathVariable UUID id) {
        return service.checkNow(id);
    }

    @PutMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    RuleSourceService.Activation setActive(@PathVariable UUID id, @Valid @RequestBody ActiveRequest request) {
        return service.setActive(id, request.active());
    }

    /** Replaces the governance of the source: its class, where it may be fetched from, its identifiers, access and owner. */
    @PutMapping("/{id}/governance")
    @PreAuthorize("hasRole('ADMIN')")
    SourceGovernance setGovernance(@PathVariable UUID id, @Valid @RequestBody GovernanceRequest request) {
        return service.updateGovernance(id, request);
    }
}
