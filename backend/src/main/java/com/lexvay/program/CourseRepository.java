package com.lexvay.program;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CourseRepository {

    private static final RowMapper<CourseVersion> VERSION = (rs, i) -> new CourseVersion(
            rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), rs.getObject("program_id", UUID.class),
            rs.getString("catalog_year"), rs.getBoolean("required"), rs.getObject("supersedes_id", UUID.class));

    private final JdbcClient jdbc;

    public CourseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertCourse(UUID id, UUID tenantId, UUID programId, String code, String title, UUID createdBy) {
        jdbc.sql("INSERT INTO course (id, tenant_id, program_id, code, title, created_by) VALUES (:id, :tenantId, :programId, :code, :title, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("programId", programId)
                .param("code", code).param("title", title).param("createdBy", createdBy).update();
    }

    public void insertVersion(UUID id, UUID tenantId, UUID courseId, String catalogYear, boolean required, UUID supersedesId, UUID createdBy) {
        jdbc.sql("INSERT INTO course_version (id, tenant_id, course_id, catalog_year, required, supersedes_id, created_by) "
                        + "VALUES (:id, :tenantId, :courseId, :catalogYear, :required, :supersedesId, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("courseId", courseId).param("catalogYear", catalogYear)
                .param("required", required).param("supersedesId", supersedesId).param("createdBy", createdBy).update();
    }

    public Optional<CourseVersion> findVersion(UUID versionId) {
        return jdbc.sql("SELECT cv.id, cv.course_id, c.program_id, cv.catalog_year, cv.required, cv.supersedes_id "
                        + "FROM course_version cv JOIN course c ON c.id = cv.course_id WHERE cv.id = :id")
                .param("id", versionId).query(VERSION).optional();
    }

    public Optional<Course> findById(UUID courseId) {
        List<Course> courses = findCourses("WHERE c.id = :id", Map.of("id", courseId));
        return courses.stream().findFirst();
    }

    public List<Course> findByProgram(UUID programId) {
        return findCourses("WHERE c.program_id = :programId", Map.of("programId", programId));
    }

    private List<Course> findCourses(String where, Map<String, Object> params) {
        var spec = jdbc.sql("SELECT c.id, c.program_id, c.code, c.title FROM course c " + where + " ORDER BY c.code");
        params.forEach(spec::param);
        Map<UUID, Course> byId = new LinkedHashMap<>();
        spec.query((rs, i) -> byId.put(rs.getObject("id", UUID.class), new Course(rs.getObject("id", UUID.class),
                rs.getObject("program_id", UUID.class), rs.getString("code"), rs.getString("title"), new ArrayList<>()))).list();
        if (byId.isEmpty()) {
            return List.of();
        }
        jdbc.sql("SELECT cv.id, cv.course_id, c.program_id, cv.catalog_year, cv.required, cv.supersedes_id "
                        + "FROM course_version cv JOIN course c ON c.id = cv.course_id WHERE cv.course_id IN (:ids) ORDER BY cv.created_at")
                .param("ids", List.copyOf(byId.keySet())).query(VERSION).list()
                .forEach(v -> byId.get(v.courseId()).versions().add(v));
        return List.copyOf(byId.values());
    }
}
