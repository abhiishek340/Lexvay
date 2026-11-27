package com.lexvay.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** GET /api/v1/users: tenant-scoped by row-level security, readable by every role, never exposes hashes. */
class UserApiTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;

    TestTenants.TenantWithUsers a;
    TestTenants.TenantWithUsers b;

    @BeforeEach
    void setUp() {
        a = tenants.create("usersa");
        b = tenants.create("usersb");
    }

    @Test
    void listsOnlyTheCallersTenantWithNamesAndRoles() throws Exception {
        var result = mvc.get().uri("/api/v1/users").with(user(a.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());

        List<String> emails = new ArrayList<>();
        body.forEach(u -> emails.add(u.get("email").asString()));
        assertThat(emails).containsExactlyInAnyOrder(a.admin().email(), a.contributor().email(),
                a.reviewer().email(), a.signer().email());
        assertThat(emails).doesNotContain(b.admin().email(), b.signer().email());

        JsonNode signer = null;
        for (JsonNode u : body) {
            if (u.get("id").asString().equals(a.signer().id().toString())) {
                signer = u;
            }
        }
        assertThat(signer).isNotNull();
        assertThat(signer.get("displayName").asString()).isEqualTo(a.signer().displayName());
        assertThat(signer.get("role").asString()).isEqualTo("SIGNER");
        assertThat(signer.has("passwordHash")).isFalse();
        assertThat(signer.has("tenantId")).isFalse();
    }

    @Test
    void everyRoleMayReadTheList() {
        for (var who : List.of(a.contributor(), a.reviewer(), a.signer(), a.admin())) {
            assertThat(mvc.get().uri("/api/v1/users").with(user(who)).exchange()).hasStatus(200);
        }
    }

    @Test
    void unauthenticatedRequestIs401() {
        assertThat(mvc.get().uri("/api/v1/users").exchange()).hasStatus(401);
    }
}
