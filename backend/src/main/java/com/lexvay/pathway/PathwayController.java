package com.lexvay.pathway;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class PathwayController {

    private final PathwayService service;

    public PathwayController(PathwayService service) {
        this.service = service;
    }

    /** Open to every signed-in role. Without {@code catalogYear} it reads the program's own. */
    @GetMapping("/programs/{programId}/pathway")
    PathwayView read(@PathVariable UUID programId, @RequestParam(required = false) @Size(max = 20) String catalogYear) {
        return service.read(programId, catalogYear);
    }

    @PostMapping("/programs/{programId}/pathway-groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMIN')")
    PathwayView create(@PathVariable UUID programId, @Valid @RequestBody CreatePathwayGroupRequest request) {
        return service.create(programId, request);
    }

    @PostMapping("/pathway-groups/{groupId}/retire")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMIN')")
    PathwayView retire(@PathVariable UUID groupId, @Valid @RequestBody RetirePathwayGroupRequest request) {
        return service.retire(groupId, request);
    }
}
