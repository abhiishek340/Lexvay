package com.lexvay.audit;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AuditFilterApiTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;

    TestTenants.TenantWithUsers t;
    UUID firstProgram;
    UUID secondProgram;

    @BeforeEach
    void setUp() throws Exception {
        t = tenants.create("audit-filter");
        firstProgram = createProgram(t.contributor(), "First");
        secondProgram = createProgram(t.admin(), "Second");
    }

    private UUID createProgram(LexvayUser as, String name) throws Exception {
        var created = mvc.post().uri("/api/v1/programs").with(user(as)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", name, "licenseType", "LPC", "catalogYear", "2026-27",
                        "jurisdictions", List.of("CA"))))
                .exchange();
        assertThat(created).hasStatus(201);
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asString());
    }

    private List<JsonNode> audit(String... params) throws Exception {
        var request = mvc.get().uri("/api/v1/audit").with(user(t.admin()));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        var result = request.exchange();
        assertThat(result).hasStatus(200);
        List<JsonNode> entries = new ArrayList<>();
        json.readTree(result.getResponse().getContentAsString()).forEach(entries::add);
        return entries;
    }

    @Test
    void withoutFiltersTheShapeAndOrderAreUnchanged() throws Exception {
        List<JsonNode> all = audit();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        assertThat(all.get(0).propertyNames()).containsExactlyInAnyOrder("id", "userId", "action", "entityType", "entityId", "detail", "createdAt");
        for (int i = 1; i < all.size(); i++) {
            assertThat(all.get(i - 1).get("createdAt").asString()).isGreaterThanOrEqualTo(all.get(i).get("createdAt").asString());
        }
    }

    @Test
    void filtersByActionEntityTypeEntityIdAndUser() throws Exception {
        assertThat(audit("action", "PROGRAM_CREATED")).hasSize(2).allSatisfy(e -> assertThat(e.get("action").asString()).isEqualTo("PROGRAM_CREATED"));
        assertThat(audit("action", "NO_SUCH_ACTION")).isEmpty();
        assertThat(audit("entityType", "program")).extracting(e -> e.get("entityId").asString())
                .containsExactlyInAnyOrder(firstProgram.toString(), secondProgram.toString());

        List<JsonNode> one = audit("entityId", secondProgram.toString());
        assertThat(one).hasSize(1);
        assertThat(one.get(0).get("userId").asString()).isEqualTo(t.admin().id().toString());

        assertThat(audit("userId", t.contributor().id().toString())).hasSize(1)
                .allSatisfy(e -> assertThat(e.get("entityId").asString()).isEqualTo(firstProgram.toString()));
        assertThat(audit("action", "PROGRAM_CREATED", "userId", t.admin().id().toString(), "entityId", firstProgram.toString())).isEmpty();
    }

    @Test
    void filtersByInclusiveTimeRange() throws Exception {
        List<JsonNode> all = audit();
        String newest = all.get(0).get("createdAt").asString();
        String oldest = all.get(all.size() - 1).get("createdAt").asString();

        assertThat(audit("from", newest)).extracting(e -> e.get("id").asString()).contains(all.get(0).get("id").asString());
        assertThat(audit("to", oldest)).extracting(e -> e.get("id").asString()).contains(all.get(all.size() - 1).get("id").asString());
        assertThat(audit("from", OffsetDateTime.now().plusDays(1).toString())).isEmpty();
        assertThat(audit("to", OffsetDateTime.now().minusDays(1).toString())).isEmpty();
        assertThat(audit("from", OffsetDateTime.now().minusDays(1).toString(), "to", OffsetDateTime.now().plusDays(1).toString()))
                .hasSize(all.size());
    }

    @Test
    void aMalformedFilterIs400AndOtherTenantsRowsStayHidden() throws Exception {
        assertThat(mvc.get().uri("/api/v1/audit").param("entityId", "not-a-uuid").with(user(t.admin())).exchange()).hasStatus(400);
        assertThat(mvc.get().uri("/api/v1/audit").param("from", "yesterday").with(user(t.admin())).exchange()).hasStatus(400);

        var other = tenants.create("other");
        var asOther = mvc.get().uri("/api/v1/audit").param("entityId", firstProgram.toString()).with(user(other.admin())).exchange();
        assertThat(asOther).hasStatus(200);
        assertThat(json.readTree(asOther.getResponse().getContentAsString())).isEmpty();
    }
}
