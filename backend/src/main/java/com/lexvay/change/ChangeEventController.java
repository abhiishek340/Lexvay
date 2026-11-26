package com.lexvay.change;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/change-events")
public class ChangeEventController {

    private final ChangeService service;

    public ChangeEventController(ChangeService service) {
        this.service = service;
    }

    @GetMapping
    List<ChangeEventView> list() {
        return service.list();
    }

    /** Long operation: the re-run is a job. */
    @PostMapping("/{id}/rerun")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('REVIEWER', 'SIGNER', 'ADMIN')")
    ChangeService.RerunResult rerun(@PathVariable UUID id) {
        return service.rerun(id);
    }
}
