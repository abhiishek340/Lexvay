package com.lexvay.program;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/versions")
public class CourseVersionController {

    private final CourseService service;

    public CourseVersionController(CourseService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMIN')")
    Course addVersion(@PathVariable UUID courseId, @Valid @RequestBody AddCourseVersionRequest request) {
        return service.addVersion(courseId, request);
    }
}
