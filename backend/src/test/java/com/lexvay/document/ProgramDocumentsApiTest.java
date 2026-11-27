package com.lexvay.document;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.auth.LexvayUser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

/** GET /programs/{id}/documents: the document picker of the evidence request screens (F4). */
class ProgramDocumentsApiTest extends AbstractAssessmentTest {

    UUID program;
    UUID plan;
    UUID pending;
    UUID oldSyllabus;
    UUID newSyllabus;
    UUID elective;
    UUID cv605;
    UUID cv605Next;
    UUID cv650;

    @BeforeEach
    void documents() {
        program = fx.program(t, List.of("CA"));
        cv605 = fx.course(t, program, "COUN 605", true);
        cv650 = fx.course(t, program, "COUN 650", false);
        UUID course605 = UUID.fromString(fx.read(mvc.get().uri("/api/v1/programs/" + program + "/courses").with(user(t.reviewer())).exchange())
                .get(0).get("id").asString());
        var next = mvc.post().uri("/api/v1/courses/" + course605 + "/versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("catalogYear", "2027-28", "required", true, "supersedesId", cv605.toString())))
                .exchange();
        assertThat(next).hasStatus(201);
        cv605Next = UUID.fromString(fx.read(next).get("versions").get(1).get("id").asString());

        plan = fx.document(t, program, "degree-plan.txt", "DEGREE_PLAN", null, DEGREE_PLAN);
        oldSyllabus = fx.document(t, program, "coun605-2026.txt", "SYLLABUS", cv605, SYLLABUS_605);
        newSyllabus = fx.document(t, program, "coun605-2027.txt", "SYLLABUS", cv605Next, SYLLABUS_605 + "Revised for 2027-28.\n");
        elective = fx.document(t, program, "coun650.txt", "SYLLABUS", cv650, SYLLABUS_650);
        fx.approvedRuleVersion(t, "CA", "Demo rule set California", "Fictional CA rule.",
                fx.requirements("CA", List.of(AssessmentFixtures.subject("Ethics", "coursework in ethics"))));
        // Uploaded but not ingested yet: the job has not run, so the document has no pages.
        var upload = mvc.perform(multipart("/api/v1/programs/" + program + "/documents")
                .file(new MockMultipartFile("file", "handbook.txt", "text/plain", "Fictional handbook.".getBytes(StandardCharsets.UTF_8)))
                .param("kind", "HANDBOOK").with(user(t.contributor())).with(csrf()));
        assertThat(upload).hasStatus(202);
        pending = UUID.fromString(fx.read(upload).get("documentId").asString());
    }

    private JsonNode list(LexvayUser as) {
        var result = mvc.get().uri("/api/v1/programs/" + program + "/documents").with(user(as)).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result);
    }

    @Test
    void listsProgramDocumentsFirstThenCoursesWithTheNewestCatalogYearFirst() {
        JsonNode documents = list(t.contributor());
        List<String> ids = new ArrayList<>();
        documents.forEach(d -> ids.add(d.get("id").asString()));
        assertThat(ids).containsExactly(plan.toString(), pending.toString(), newSyllabus.toString(), oldSyllabus.toString(),
                elective.toString());

        JsonNode degreePlan = documents.get(0);
        assertThat(degreePlan.get("filename").asString()).isEqualTo("degree-plan.txt");
        assertThat(degreePlan.get("kind").asString()).isEqualTo("DEGREE_PLAN");
        assertThat(degreePlan.get("courseVersionId").isNull()).isTrue();
        assertThat(degreePlan.get("courseCode").isNull()).isTrue();
        assertThat(degreePlan.get("courseTitle").isNull()).isTrue();
        assertThat(degreePlan.get("catalogYear").isNull()).isTrue();
        assertThat(degreePlan.get("courseRequired").isNull()).isTrue();
        assertThat(degreePlan.get("pageCount").asInt()).isEqualTo(1);
        assertThat(degreePlan.get("current").asBoolean()).as("program-level documents are current").isTrue();
        assertThat(degreePlan.get("createdAt").asString()).isNotBlank();

        assertThat(documents.get(1).get("pageCount").asInt()).as("not ingested yet").isZero();
        assertThat(documents.get(1).get("current").asBoolean()).isTrue();

        JsonNode current = documents.get(2);
        assertThat(current.get("courseVersionId").asString()).isEqualTo(cv605Next.toString());
        assertThat(current.get("courseCode").asString()).isEqualTo("COUN 605");
        assertThat(current.get("courseTitle").asString()).isEqualTo("Course COUN 605");
        assertThat(current.get("catalogYear").asString()).isEqualTo("2027-28");
        assertThat(current.get("courseRequired").asBoolean()).isTrue();
        assertThat(current.get("current").asBoolean()).isTrue();

        JsonNode superseded = documents.get(3);
        assertThat(superseded.get("catalogYear").asString()).isEqualTo("2026-27");
        assertThat(superseded.get("current").asBoolean()).as("its course version is superseded").isFalse();

        JsonNode optional = documents.get(4);
        assertThat(optional.get("courseCode").asString()).isEqualTo("COUN 650");
        assertThat(optional.get("courseRequired").asBoolean()).isFalse();
        assertThat(optional.get("current").asBoolean()).isTrue();

        documents.forEach(d -> assertThat(d.get("kind").asString()).as("rule text belongs to no program").isNotEqualTo("RULE_TEXT"));
    }

    @Test
    void everyRoleMayReadAndAnonymousCallersMayNot() {
        for (LexvayUser who : List.of(t.contributor(), t.reviewer(), t.signer(), t.admin())) {
            assertThat(list(who)).as(who.role().name()).hasSize(5);
        }
        assertThat(mvc.get().uri("/api/v1/programs/" + program + "/documents").exchange()).hasStatus(401);
    }

    @Test
    void anotherTenantAndAnUnknownProgramAreNotFound() {
        var other = tenants.create("other");
        assertThat(mvc.get().uri("/api/v1/programs/" + program + "/documents").with(user(other.admin())).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/programs/" + UUID.randomUUID() + "/documents").with(user(t.reviewer())).exchange()).hasStatus(404);
        UUID empty = fx.program(t, List.of("CA"));
        assertThat(mvc.get().uri("/api/v1/programs/" + empty + "/documents").with(user(t.reviewer())).exchange()).hasStatus(200);
    }
}
