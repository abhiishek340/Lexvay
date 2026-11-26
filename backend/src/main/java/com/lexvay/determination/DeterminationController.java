package com.lexvay.determination;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/programs/{programId}/determinations")
public class DeterminationController {

    private final DeterminationService service;

    public DeterminationController(DeterminationService service) {
        this.service = service;
    }

    /** Product rule 1: only the signer role signs. Not even ADMIN. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SIGNER')")
    Determination sign(@PathVariable UUID programId, @Valid @RequestBody SignRequest request) {
        return service.sign(programId, request);
    }

    @GetMapping
    List<Determination> list(@PathVariable UUID programId) {
        return service.list(programId);
    }
}
