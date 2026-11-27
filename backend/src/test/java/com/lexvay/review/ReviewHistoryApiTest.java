package com.lexvay.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.common.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class ReviewHistoryApiTest extends AbstractAssessmentTest {

    @Autowired ReviewRepository reviews;

    ReviewFixtures rf;
    ReviewFixtures.Scenario s;

    @BeforeEach
    void scenario() {
        rf = new ReviewFixtures(mvc, json, fx);
        s = rf.californiaScenario(t, "history " + UUID.randomUUID().toString().substring(0, 6));
    }

    @Test
    void listsEventsOldestFirstWithUserDisplayNamesAndNullForTheSystem() throws Exception {
        assertThat(rf.review(t.reviewer(), s.foundMapping(), "EVIDENCE_REQUESTED", "Please send the syllabus.")).hasStatus(201);
        assertThat(rf.review(t.signer(), s.foundMapping(), "ACCEPTED", null)).hasStatus(201);
        TenantContext.runAs(t.tenantId(), () -> reviews.insert(t.tenantId(), s.foundMapping(), ReviewKind.REOPENED, null, null));

        var result = mvc.get().uri("/api/v1/mappings/" + s.foundMapping() + "/reviews").with(user(t.contributor())).exchange();
        assertThat(result).hasStatus(200);
        JsonNode events = fx.read(result);
        assertThat(events).hasSize(3);
        assertThat(events.get(0).get("kind").asString()).isEqualTo("EVIDENCE_REQUESTED");
        assertThat(events.get(0).get("note").asString()).isEqualTo("Please send the syllabus.");
        assertThat(events.get(0).get("userId").asString()).isEqualTo(t.reviewer().id().toString());
        assertThat(events.get(0).get("userDisplayName").asString()).isEqualTo(t.reviewer().displayName());
        assertThat(events.get(1).get("kind").asString()).isEqualTo("ACCEPTED");
        assertThat(events.get(1).get("note").isNull()).isTrue();
        assertThat(events.get(1).get("userDisplayName").asString()).isEqualTo(t.signer().displayName());
        assertThat(events.get(2).get("kind").asString()).isEqualTo("REOPENED");
        assertThat(events.get(2).get("userId").isNull()).isTrue();
        assertThat(events.get(2).get("userDisplayName").isNull()).isTrue();
        for (JsonNode event : events) {
            assertThat(event.get("id").asString()).isNotBlank();
            assertThat(event.get("createdAt").asString()).isNotBlank();
        }
        assertThat(events.get(0).get("createdAt").asString()).isLessThanOrEqualTo(events.get(1).get("createdAt").asString());
    }

    @Test
    void aMappingWithoutDecisionsHasAnEmptyHistory() throws Exception {
        assertThat(fx.read(mvc.get().uri("/api/v1/mappings/" + s.missingMapping() + "/reviews").with(user(t.reviewer())).exchange())).isEmpty();
    }

    @Test
    void anUnknownMappingAndAnotherTenantsMappingAre404() {
        var other = tenants.create("other");
        assertThat(mvc.get().uri("/api/v1/mappings/" + s.foundMapping() + "/reviews").with(user(other.admin())).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/mappings/" + UUID.randomUUID() + "/reviews").with(user(t.admin())).exchange()).hasStatus(404);
    }

    @Test
    void unauthenticatedGets401() {
        assertThat(mvc.get().uri("/api/v1/mappings/" + s.foundMapping() + "/reviews").exchange()).hasStatus(401);
    }
}
