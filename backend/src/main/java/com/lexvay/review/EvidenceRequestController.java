package com.lexvay.review;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EvidenceRequestController {

    private final EvidenceRequestService service;

    public EvidenceRequestController(EvidenceRequestService service) {
        this.service = service;
    }

    @PostMapping("/mappings/{id}/evidence-responses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'REVIEWER', 'SIGNER', 'ADMIN')")
    EvidenceResponseView respond(@PathVariable UUID id, @Valid @RequestBody EvidenceResponseRequest request) {
        return service.respond(id, request);
    }

    @GetMapping("/mappings/{id}/evidence-responses")
    List<EvidenceResponseView> responses(@PathVariable UUID id) {
        return service.responses(id);
    }

    @GetMapping("/programs/{programId}/evidence-requests")
    List<EvidenceRequestView> requests(@PathVariable UUID programId,
                                       @RequestParam(defaultValue = "OPEN") EvidenceRequestStatus status) {
        return service.requests(programId, status);
    }
}
