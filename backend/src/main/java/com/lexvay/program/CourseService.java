package com.lexvay.program;

import com.lexvay.audit.AuditService;
import com.lexvay.change.ChangeService;
import com.lexvay.common.NotFoundException;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

    private final CourseRepository courses;
    private final ProgramService programs;
    private final AuditService audit;
    private final ChangeService changes;

    public CourseService(CourseRepository courses, ProgramService programs, AuditService audit, ChangeService changes) {
        this.courses = courses;
        this.programs = programs;
        this.audit = audit;
        this.changes = changes;
    }

    /** A new catalog-year version of a course (spec section 7). Defaults to superseding the newest version (D-068). */
    @Transactional
    public Course addVersion(UUID courseId, AddCourseVersionRequest request) {
        Course course = courses.findById(courseId).orElseThrow(() -> new NotFoundException("Course not found"));
        CourseVersion previous;
        if (request.supersedesId() != null) {
            previous = course.versions().stream().filter(v -> v.id().equals(request.supersedesId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("The superseded version does not belong to this course"));
        } else {
            previous = course.versions().get(course.versions().size() - 1);
        }
        if (course.versions().stream().anyMatch(v -> previous.id().equals(v.supersedesId()))) {
            throw new IllegalArgumentException("That version has already been superseded");
        }
        UUID tenantId = TenantContext.require();
        UUID actor = CurrentUser.require().id();
        UUID versionId = UUID.randomUUID();
        courses.insertVersion(versionId, tenantId, courseId, request.catalogYear().trim(), request.required(), previous.id(), actor);
        UUID eventId = changes.onCourseVersionAdded(previous.id(), versionId, course.code());
        audit.record("COURSE_VERSION_ADDED", "course", courseId, Map.of("courseVersionId", versionId.toString(),
                "supersedesId", previous.id().toString(), "catalogYear", request.catalogYear().trim(), "required", request.required(),
                "changeEventId", eventId.toString()));
        return courses.findById(courseId).orElseThrow();
    }

    /** Creates a course and its first version for the program's catalog year (D-039). */
    @Transactional
    public Course create(UUID programId, CreateCourseRequest request) {
        Program program = programs.get(programId);
        UUID tenantId = TenantContext.require();
        UUID actor = CurrentUser.require().id();
        UUID courseId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        courses.insertCourse(courseId, tenantId, programId, request.code().trim(), request.title().trim(), actor);
        courses.insertVersion(versionId, tenantId, courseId, program.catalogYear(), request.required(), null, actor);
        audit.record("COURSE_CREATED", "course", courseId, Map.of(
                "programId", programId.toString(), "code", request.code().trim(), "title", request.title().trim(),
                "required", request.required(), "courseVersionId", versionId.toString()));
        return courses.findById(courseId).orElseThrow();
    }

    public List<Course> list(UUID programId) {
        programs.get(programId);
        return courses.findByProgram(programId);
    }
}
