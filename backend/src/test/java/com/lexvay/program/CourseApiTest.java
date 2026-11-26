package com.lexvay.program;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.audit.AuditRepository;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.ObjectMapper;

class CourseApiTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired AuditRepository audit;

    TestTenants.TenantWithUsers t;
    UUID programId;

    @BeforeEach
    void setUp() throws Exception {
        t = tenants.create("courses");
        var created = mvc.post().uri("/api/v1/programs").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "P", "licenseType", "L", "catalogYear", "2026-27", "jurisdictions", List.of("CA"))))
                .exchange();
        programId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asString());
    }

    private String course(String code, boolean required) {
        return json.writeValueAsString(Map.of("code", code, "title", "Course " + code, "required", required));
    }

    @Test
    void createWritesAuditRow() throws Exception {
        var result = mvc.post().uri("/api/v1/programs/" + programId + "/courses").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(course("COUN 605", true)).exchange();
        assertThat(result).hasStatus(201);
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("COUN 605");
        assertThat(body.get("versions")).hasSize(1);
        assertThat(body.get("versions").get(0).get("catalogYear").asString()).isEqualTo("2026-27");
        assertThat(body.get("versions").get(0).get("required").asBoolean()).isTrue();
        UUID courseId = UUID.fromString(body.get("id").asString());

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(courseId));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).action()).isEqualTo("COURSE_CREATED");
        assertThat(entries.get(0).userId()).isEqualTo(t.contributor().id());
    }

    @Test
    void duplicateCodeIs409AndListWorks() throws Exception {
        mvc.post().uri("/api/v1/programs/" + programId + "/courses").with(user(t.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(course("COUN 610", true)).exchange();
        var dup = mvc.post().uri("/api/v1/programs/" + programId + "/courses").with(user(t.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(course("COUN 610", false)).exchange();
        assertThat(dup).hasStatus(409);
        var list = mvc.get().uri("/api/v1/programs/" + programId + "/courses").with(user(t.reviewer())).exchange();
        assertThat(json.readTree(list.getResponse().getContentAsString())).hasSize(1);
    }

    @Test
    void reviewerMayNotCreateCourses() {
        var result = mvc.post().uri("/api/v1/programs/" + programId + "/courses").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(course("COUN 615", true)).exchange();
        assertThat(result).hasStatus(403);
    }

    @Test
    void otherTenantGets404() {
        var other = tenants.create("other");
        var result = mvc.post().uri("/api/v1/programs/" + programId + "/courses").with(user(other.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(course("COUN 615", true)).exchange();
        assertThat(result).hasStatus(404);
    }
}
