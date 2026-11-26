package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static com.lexvay.common.TestCsrf.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.AbstractDatabaseTest;
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

/** Product rule 7 through the HTTP API: tenant B can neither read nor write tenant A's rows. */
class Rule7_TenantIsolationApiTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;

    TestTenants.TenantWithUsers a;
    TestTenants.TenantWithUsers b;
    UUID programOfA;

    @BeforeEach
    void setUp() throws Exception {
        a = tenants.create("alpha");
        b = tenants.create("beta");
        var created = mvc.post().uri("/api/v1/programs")
                .with(user(a.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "name", "Alpha M.S. Counseling", "licenseType", "LPC", "catalogYear", "2026-27",
                        "jurisdictions", List.of("CA", "TX"))))
                .exchange();
        assertThat(created).hasStatus(201);
        programOfA = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asString());
    }

    @Test
    void tenantBListsNoneOfTenantAsPrograms() throws Exception {
        var result = mvc.get().uri("/api/v1/programs").with(user(b.admin())).exchange();
        assertThat(result).hasStatus(200);
        assertThat(json.readTree(result.getResponse().getContentAsString())).isEmpty();

        var own = mvc.get().uri("/api/v1/programs").with(user(a.reviewer())).exchange();
        assertThat(json.readTree(own.getResponse().getContentAsString())).hasSize(1);
    }

    @Test
    void tenantBGets404ForTenantAsProgramById() {
        assertThat(mvc.get().uri("/api/v1/programs/" + programOfA).with(user(b.admin())).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/programs/" + programOfA).with(user(a.admin())).exchange()).hasStatus(200);
    }

    @Test
    void tenantBSeesNoneOfTenantAsAuditRows() throws Exception {
        var result = mvc.get().uri("/api/v1/audit").with(user(b.admin())).exchange();
        var entries = json.readTree(result.getResponse().getContentAsString());
        for (var entry : entries) {
            assertThat(entry.get("entityId").asString()).isNotEqualTo(programOfA.toString());
        }
        var ownAudit = mvc.get().uri("/api/v1/audit").with(user(a.admin())).exchange();
        assertThat(ownAudit.getResponse().getContentAsString()).contains(programOfA.toString());
    }
}
