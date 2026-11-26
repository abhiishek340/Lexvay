package com.lexvay.program;

import static org.assertj.core.api.Assertions.assertThat;
import static com.lexvay.common.TestCsrf.csrf;
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

class ProgramApiTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired AuditRepository audit;

    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        t = tenants.create("northfield");
    }

    private String body(Map<String, ?> map) {
        return json.writeValueAsString(map);
    }

    @Test
    void createWritesAuditRow() throws Exception {
        var result = mvc.post().uri("/api/v1/programs").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "M.S. Clinical Mental Health Counseling", "licenseType", "CLINICAL_MENTAL_HEALTH_COUNSELING",
                        "catalogYear", "2026-27", "jurisdictions", List.of("UT", "CO", "AZ", "CA", "TX"))))
                .exchange();
        assertThat(result).hasStatus(201);
        var created = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("id").asString());
        assertThat(created.get("jurisdictions")).hasSize(5);
        assertThat(created.get("jurisdictions").get(0).asString()).isEqualTo("AZ");

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(id));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).action()).isEqualTo("PROGRAM_CREATED");
        assertThat(entries.get(0).userId()).isEqualTo(t.contributor().id());
        assertThat(entries.get(0).detail().get("jurisdictions")).isEqualTo(List.of("AZ", "CA", "CO", "TX", "UT"));
    }

    @Test
    void reviewerAndSignerMayNotCreatePrograms() {
        for (var who : List.of(t.reviewer(), t.signer())) {
            var result = mvc.post().uri("/api/v1/programs").with(user(who)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("name", "x", "licenseType", "y", "catalogYear", "z", "jurisdictions", List.of("CA"))))
                    .exchange();
            assertThat(result).hasStatus(403);
        }
    }

    @Test
    void unknownJurisdictionIsRejectedWithProblemDetail() throws Exception {
        var result = mvc.post().uri("/api/v1/programs").with(user(t.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "x", "licenseType", "y", "catalogYear", "z", "jurisdictions", List.of("ZZ"))))
                .exchange();
        assertThat(result).hasStatus(400);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
        assertThat(result.getResponse().getContentAsString()).contains("ZZ");
    }

    @Test
    void validationErrorsAreProblemDetails() {
        var result = mvc.post().uri("/api/v1/programs").with(user(t.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "", "licenseType", "y", "catalogYear", "z", "jurisdictions", List.of())))
                .exchange();
        assertThat(result).hasStatus(400);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
    }

    @Test
    void unknownProgramIs404() {
        assertThat(mvc.get().uri("/api/v1/programs/" + UUID.randomUUID()).with(user(t.admin())).exchange()).hasStatus(404);
    }

    @Test
    void stateChangeWithoutCsrfTokenIsRejected() {
        var result = mvc.post().uri("/api/v1/programs").with(user(t.admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "x", "licenseType", "y", "catalogYear", "z", "jurisdictions", List.of("CA"))))
                .exchange();
        assertThat(result).hasStatus(403);
    }
}
