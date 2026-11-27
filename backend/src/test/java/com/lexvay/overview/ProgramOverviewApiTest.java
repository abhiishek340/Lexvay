package com.lexvay.overview;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.review.ReviewFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class ProgramOverviewApiTest extends AbstractAssessmentTest {

    ReviewFixtures rf;
    ReviewFixtures.Scenario s;

    @BeforeEach
    void scenario() {
        rf = new ReviewFixtures(mvc, json, fx);
        s = rf.californiaScenario(t, "overview " + UUID.randomUUID().toString().substring(0, 6));
    }

    private JsonNode overview(UUID programId) throws Exception {
        var result = mvc.get().uri("/api/v1/programs/" + programId + "/overview").with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result);
    }

    @Test
    void returnsProgramLatestFinishedAssessmentDeterminationsAndUndecidedTotalInOneResponse() throws Exception {
        JsonNode before = overview(s.programId());
        assertThat(before.get("program").get("id").asString()).isEqualTo(s.programId().toString());
        assertThat(before.get("assessment").get("id").asString()).isEqualTo(s.assessmentId().toString());
        assertThat(before.get("assessment").get("status").asString()).isEqualTo("FINISHED");
        assertThat(before.get("determinations")).isEmpty();
        assertThat(before.get("openChangeEvents")).isEmpty();
        long total = 0;
        for (JsonNode j : before.get("assessment").get("jurisdictions")) {
            total += j.get("total").asLong();
        }
        assertThat(before.get("undecidedTotal").asLong()).as("nothing is decided yet").isEqualTo(total);

        rf.decideAll(t, s);
        JsonNode after = overview(s.programId());
        assertThat(after.get("undecidedTotal").asLong()).isEqualTo(total - 3);
        // The CA list is fully decided, so it can be signed; the response then carries the determination.
        assertThat(rf.sign(t.signer(), s.programId(), "CA", "MEETS")).hasStatus(201);
        JsonNode signed = overview(s.programId());
        assertThat(signed.get("determinations")).hasSize(1);
        assertThat(signed.get("determinations").get(0).get("jurisdiction").asString()).isEqualTo("CA");
    }

    @Test
    void aProgramWithoutAFinishedAssessmentHasNullAssessmentAndZeroUndecided() throws Exception {
        UUID empty = fx.program(t, List.of("CA"));
        JsonNode view = overview(empty);
        assertThat(view.get("assessment").isNull()).isTrue();
        assertThat(view.get("undecidedTotal").asLong()).isZero();
        assertThat(view.get("determinations")).isEmpty();
    }

    @Test
    void openChangeEventsAreTheOnesThatTouchThisProgram() throws Exception {
        UUID otherProgram = fx.program(t, List.of("CA"));
        JsonNode courses = fx.read(mvc.get().uri("/api/v1/programs/" + s.programId() + "/courses").with(user(t.reviewer())).exchange());
        String courseId = courses.get(0).get("id").asString();
        var added = mvc.post().uri("/api/v1/courses/" + courseId + "/versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"catalogYear\":\"2027-28\",\"required\":true}").exchange();
        assertThat(added).hasStatus(201);

        JsonNode events = overview(s.programId()).get("openChangeEvents");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("kind").asString()).isEqualTo("COURSE_VERSION");
        assertThat(events.get(0).get("status").asString()).isEqualTo("OPEN");
        assertThat(overview(otherProgram).get("openChangeEvents")).as("another program of the same tenant is not affected").isEmpty();
    }

    @Test
    void anotherTenantGets404() {
        var other = tenants.create("other");
        assertThat(mvc.get().uri("/api/v1/programs/" + s.programId() + "/overview").with(user(other.admin())).exchange()).hasStatus(404);
    }

    @Test
    void unauthenticatedGets401() {
        assertThat(mvc.get().uri("/api/v1/programs/" + s.programId() + "/overview").exchange()).hasStatus(401);
    }
}
