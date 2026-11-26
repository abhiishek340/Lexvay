package com.lexvay.change;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.review.ReviewFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

class CourseVersionChangeTest extends AbstractAssessmentTest {

    private MvcTestResult addVersion(UUID courseId, String year) {
        return mvc.post().uri("/api/v1/courses/" + courseId + "/versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("catalogYear", year, "required", true))).exchange();
    }

    @Test
    void newCourseVersionCreatesEventMarksStaleAndReopens() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        UUID cv605 = fx.course(t, programId, "COUN 605", true);
        UUID syllabus = fx.document(t, programId, "coun605.txt", "SYLLABUS", cv605, SYLLABUS_605);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah course change", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"),
                AssessmentFixtures.subject("Confidentiality", "counseling coursework on confidentiality and duty to warn"))));
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1,
                "legal standards, confidentiality, duty to warn,\nand professional identity of clinical mental health counselors.", "Ethics content."));
        fake.script("MATCH:UT-2", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1,
                "Week 3: Legal standards, confidentiality and duty to warn in counseling practice.", "Week 3 covers it."));
        UUID assessmentId = fx.runAssessment(t, programId);
        var rf = new ReviewFixtures(mvc, json, fx);
        var mappings = fx.mappings(t, assessmentId, "UT");
        UUID ethicsMapping = UUID.fromString(fx.mappingFor(mappings, "UT-1").get("id").asString());
        UUID week3Mapping = UUID.fromString(fx.mappingFor(mappings, "UT-2").get("id").asString());
        rf.review(t.reviewer(), ethicsMapping, "ACCEPTED", null);
        rf.review(t.reviewer(), week3Mapping, "ACCEPTED", null);
        UUID signed = UUID.fromString(fx.read(rf.sign(t.signer(), programId, "UT", "MEETS")).get("id").asString());

        JsonNode courses = fx.read(mvc.get().uri("/api/v1/programs/" + programId + "/courses").with(user(t.reviewer())).exchange());
        UUID courseId = UUID.fromString(courses.get(0).get("id").asString());
        var added = addVersion(courseId, "2027-28");
        assertThat(added).hasStatus(201);
        assertThat(fx.read(added).get("versions")).hasSize(2);
        UUID newVersion = UUID.fromString(fx.read(added).get("versions").get(1).get("id").asString());
        assertThat(fx.read(added).get("versions").get(1).get("supersedesId").asString()).isEqualTo(cv605.toString());
        assertThat(addVersion(courseId, "2028-29")).as("the newest version is superseded; a third version supersedes it in turn").hasStatus(201);

        var events = fx.read(mvc.get().uri("/api/v1/change-events").with(user(t.reviewer())).exchange());
        var event = events.get(events.size() - 1);
        assertThat(event.get("kind").asString()).isEqualTo("COURSE_VERSION");
        assertThat(event.get("courseCode").asString()).isEqualTo("COUN 605");
        assertThat(event.get("oldCatalogYear").asString()).isEqualTo("2026-27");
        assertThat(event.get("newCatalogYear").asString()).isEqualTo("2027-28");
        assertThat(event.get("newVersionHasDocument").asBoolean()).isFalse();
        assertThat(event.get("reopenedDeterminationIds").get(0).asString()).isEqualTo(signed.toString());
        UUID eventId = UUID.fromString(event.get("id").asString());

        var stale = fx.mappings(t, assessmentId, "UT");
        assertThat(stale).allSatisfy(m -> assertThat(m.get("stale").asBoolean()).isTrue());
        var tooEarly = mvc.post().uri("/api/v1/change-events/" + eventId + "/rerun").with(user(t.reviewer())).with(csrf()).exchange();
        assertThat(tooEarly).hasStatus(409);
        assertThat(fx.read(tooEarly).get("detail").asString()).contains("Upload");

        // The 2027-28 syllabus keeps the Week 3 sentence but rewrites the ethics description.
        String revisedSyllabus = SYLLABUS_605.replace("legal standards, confidentiality, duty to warn,\nand professional identity of clinical mental health counselors.",
                "ethical decision-making models and the counselor's professional identity.");
        UUID newDocument = fx.document(t, programId, "coun605-2027.txt", "SYLLABUS", newVersion, revisedSyllabus);
        assertThat(mvc.post().uri("/api/v1/change-events/" + eventId + "/rerun").with(user(t.reviewer())).with(csrf()).exchange()).hasStatus(202);
        runner.runPending();

        var after = fx.mappings(t, assessmentId, "UT");
        var week3 = fx.mappingFor(after, "UT-2");
        assertThat(week3.get("stale").asBoolean()).isFalse();
        assertThat(week3.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(week3.get("evidence").get("documentId").asString()).as("re-cited against the new syllabus").isEqualTo(newDocument.toString());
        assertThat(week3.get("decision").get("kind").asString()).as("decision kept").isEqualTo("ACCEPTED");
        var ethics = fx.mappingFor(after, "UT-1");
        assertThat(ethics.get("stale").asBoolean()).isFalse();
        assertThat(ethics.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(ethics.get("evidence").isNull()).isTrue();
        assertThat(ethics.get("rationale").asString()).contains("no longer appears in COUN 605 (2027-28)");
        assertThat(ethics.get("decision").isNull()).as("returned to review").isTrue();
        assertThat(fx.read(mvc.get().uri("/api/v1/change-events").with(user(t.reviewer())).exchange()).get(events.size() - 1).get("status").asString()).isEqualTo("HANDLED");
    }
}
