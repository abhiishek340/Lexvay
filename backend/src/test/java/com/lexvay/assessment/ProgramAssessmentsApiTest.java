package com.lexvay.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.review.ReviewFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ProgramAssessmentsApiTest extends AbstractAssessmentTest {

    @Test
    void listsTheProgramsAssessmentsNewestFirst() throws Exception {
        var s = new ReviewFixtures(mvc, json, fx).californiaScenario(t, "list " + UUID.randomUUID().toString().substring(0, 6));
        UUID second = fx.runAssessment(t, s.programId());

        var result = mvc.get().uri("/api/v1/programs/" + s.programId() + "/assessments").with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        JsonNode list = fx.read(result);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("id").asString()).isEqualTo(second.toString());
        assertThat(list.get(1).get("id").asString()).isEqualTo(s.assessmentId().toString());
        assertThat(list.get(0).get("status").asString()).isEqualTo("FINISHED");
        assertThat(list.get(0).get("programId").asString()).isEqualTo(s.programId().toString());
    }

    @Test
    void aProgramWithoutAssessmentsHasAnEmptyList() throws Exception {
        UUID program = fx.program(t, java.util.List.of("CA"));
        assertThat(fx.read(mvc.get().uri("/api/v1/programs/" + program + "/assessments").with(user(t.reviewer())).exchange())).isEmpty();
    }

    @Test
    void anotherTenantGets404() {
        UUID program = fx.program(t, java.util.List.of("CA"));
        var other = tenants.create("other");
        assertThat(mvc.get().uri("/api/v1/programs/" + program + "/assessments").with(user(other.admin())).exchange()).hasStatus(404);
    }

    @Test
    void unauthenticatedGets401() {
        UUID program = fx.program(t, java.util.List.of("CA"));
        assertThat(mvc.get().uri("/api/v1/programs/" + program + "/assessments").exchange()).hasStatus(401);
    }
}
