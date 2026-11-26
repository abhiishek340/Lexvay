package com.lexvay.program;

import com.lexvay.audit.AuditService;
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

    public CourseService(CourseRepository courses, ProgramService programs, AuditService audit) {
        this.courses = courses;
        this.programs = programs;
        this.audit = audit;
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
