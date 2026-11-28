package com.lexvay.pathway;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/** The pathway API (AC-1): reading, adding and retiring groups, with credits on course versions. */
class PathwayApiTest extends AbstractAssessmentTest {

    static final String HANDBOOK = """
            Fictional. Prototype data. Student handbook, 2026-27.
            Every student completes the core courses and chooses two of the three electives offered.
            """;

    @Autowired AuditRepository audit;
    @Autowired JdbcClient jdbc;

    UUID programId;
    Map<String, UUID> course;

    @BeforeEach
    void setUp() {
        programId = fx.program(t, List.of("CA"));
        for (String code : List.of("COUN 605", "COUN 610", "COUN 650", "COUN 655", "COUN 645")) {
            fx.courseOnly(t, programId, code, code.compareTo("COUN 640") < 0, code.equals("COUN 610") ? "6" : "3");
        }
        course = fx.courseIds(t, programId);
    }

    private List<UUID> ids(String... codes) {
        return java.util.Arrays.stream(codes).map(course::get).toList();
    }

    private JsonNode pathway(String query) {
        var result = mvc.get().uri("/api/v1/programs/" + programId + "/pathway" + query).with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result);
    }

    private static JsonNode group(JsonNode pathway, String label) {
        for (JsonNode g : pathway.get("groups")) {
            if (g.get("label").asString().equals(label)) {
                return g;
            }
        }
        throw new AssertionError("No group " + label + " in " + pathway);
    }

    private static JsonNode member(JsonNode group, String code) {
        for (JsonNode m : group.get("members")) {
            if (m.get("code").asString().equals(code)) {
                return m;
            }
        }
        throw new AssertionError("No member " + code);
    }

    @Test
    void aProgramWithoutGroupsHasNoPathwayAndNoMinimum() {
        var view = pathway("");
        assertThat(view.get("catalogYear").asString()).isEqualTo("2026-27");
        assertThat(view.get("groups")).isEmpty();
        assertThat(view.get("minimumCredits").isNull()).isTrue();
        assertThat(view.get("unknowns").get(0).asString()).isEqualTo(PathwayCoverage.NO_GROUPS);
    }

    @Test
    void groupsAreReadWithMembersCreditsTheMinimumAndWhatEachCourseIsOn() {
        fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, ids("COUN 605", "COUN 610"));
        fx.pathwayGroup(t, programId, "2026-27", "Electives: choose 2 of 3", "CHOOSE_COUNT", 2, null, ids("COUN 650", "COUN 655", "COUN 645"));

        var view = pathway("");

        assertThat(view.get("groups")).hasSize(2);
        assertThat(view.get("groups").get(0).get("label").asString()).isEqualTo("Required courses");
        assertThat(view.get("minimumCredits").decimalValue()).isEqualByComparingTo("15");
        assertThat(view.get("minimumCreditsUnit").asString()).isEqualTo("SEMESTER");
        assertThat(view.get("unknowns")).isEmpty();
        var required = group(view, "Required courses");
        assertThat(required.get("kind").asString()).isEqualTo("ALL_OF");
        assertThat(member(required, "COUN 610").get("credits").decimalValue()).isEqualByComparingTo("6");
        assertThat(member(required, "COUN 610").get("creditUnit").asString()).isEqualTo("SEMESTER");
        assertThat(member(required, "COUN 605").get("onEveryPath").asString()).isEqualTo("TRUE");
        var electives = group(view, "Electives: choose 2 of 3");
        assertThat(electives.get("minCount").asInt()).isEqualTo(2);
        assertThat(member(electives, "COUN 650").get("onEveryPath").asString()).isEqualTo("FALSE");
        assertThat(member(electives, "COUN 650").get("onSomePath").asString()).isEqualTo("TRUE");
    }

    @Test
    void anUnrecordedMinimumIsListedAsUnknownAndThereIsNoMinimumCredits() {
        fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, ids("COUN 605"));
        fx.pathwayGroup(t, programId, "2026-27", "Electives", "CHOOSE_COUNT", null, null, ids("COUN 650", "COUN 655"));

        var view = pathway("");

        assertThat(view.get("minimumCredits").isNull()).isTrue();
        assertThat(view.get("minimumCreditsUnit").isNull()).isTrue();
        assertThat(view.get("unknowns").get(0).asString()).isEqualTo("Electives: minimum not recorded");
        assertThat(member(group(view, "Electives"), "COUN 650").get("onEveryPath").asString()).isEqualTo("UNKNOWN");
    }

    @Test
    void creatingAGroupIsAuditedWithItsCitationAndTheQuoteIsVerified() {
        UUID handbook = fx.document(t, programId, "handbook.txt", "HANDBOOK", null, HANDBOOK);
        var created = fx.pathwayGroupResult(t, programId, "2026-27", "Electives: choose 2 of 3", "CHOOSE_COUNT", 2, null,
                ids("COUN 650", "COUN 655", "COUN 645"), Map.of("documentId", handbook.toString(), "pageNumber", 1,
                        "quote", "chooses  two of the three electives offered"));
        assertThat(created).hasStatus(201);
        var made = group(fx.read(created), "Electives: choose 2 of 3");
        assertThat(made.get("citation").get("quote").asString()).isEqualTo("chooses two of the three electives offered");
        assertThat(made.get("citation").get("filename").asString()).isEqualTo("handbook.txt");
        UUID groupId = UUID.fromString(made.get("id").asString());

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(groupId));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).action()).isEqualTo("PATHWAY_GROUP_CREATED");
        assertThat(entries.get(0).userId()).isEqualTo(t.contributor().id());
        assertThat(entries.get(0).detail()).containsEntry("kind", "CHOOSE_COUNT").containsEntry("minCount", 2)
                .containsEntry("quote", "chooses two of the three electives offered");
        assertThat((List<?>) entries.get(0).detail().get("courseIds")).hasSize(3);

        var fabricated = fx.pathwayGroupResult(t, programId, "2026-27", "Electives again", "CHOOSE_COUNT", 2, null, ids("COUN 650"),
                Map.of("documentId", handbook.toString(), "pageNumber", 1, "quote", "chooses three of the four electives offered"));
        assertThat(fabricated).hasStatus(400);
        assertThat(pathway("").get("groups")).hasSize(1);
    }

    @Test
    void aGroupMustAgreeWithItsKindAndItsProgram() {
        UUID other = fx.program(t, List.of("TX"));
        fx.courseOnly(t, other, "COUN 900", true, "3");
        UUID foreignCourse = fx.courseIds(t, other).get("COUN 900");

        assertBadRequest(fx.pathwayGroupResult(t, programId, "2026-27", "G", "ALL_OF", 1, null, ids("COUN 605"), null), "minCount belongs");
        assertBadRequest(fx.pathwayGroupResult(t, programId, "2026-27", "G", "CHOOSE_COUNT", null, "6", ids("COUN 605"), null), "minCredits belongs");
        assertBadRequest(fx.pathwayGroupResult(t, programId, "2026-27", "G", "CHOOSE_COUNT", 3, null, ids("COUN 605", "COUN 610"), null), "cannot ask for 3 of 2");
        assertBadRequest(fx.pathwayGroupResult(t, programId, "2026-27", "G", "ALL_OF", null, null, List.of(course.get("COUN 605"), course.get("COUN 605")), null), "once in a group");
        assertBadRequest(fx.pathwayGroupResult(t, programId, "2026-27", "G", "ALL_OF", null, null, List.of(foreignCourse), null), "belong to this program");
        assertThat(pathway("").get("groups")).isEmpty();
    }

    private void assertBadRequest(org.springframework.test.web.servlet.assertj.MvcTestResult result, String detail) {
        assertThat(result).hasStatus(400);
        assertThat(fx.read(result).get("detail").asString()).contains(detail);
    }

    @Test
    void onlyContributorsAndAdminsAddAndRetireGroupsAndAnyRoleReadsThem() {
        var refused = mvc.post().uri("/api/v1/programs/" + programId + "/pathway-groups").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("catalogYear", "2026-27", "label", "G", "kind", "ALL_OF", "courseIds", ids("COUN 605"))))
                .exchange();
        assertThat(refused).hasStatus(403);
        var made = fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, ids("COUN 605"));
        UUID groupId = UUID.fromString(group(made, "Required courses").get("id").asString());
        assertThat(mvc.post().uri("/api/v1/pathway-groups/" + groupId + "/retire").with(user(t.signer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}").exchange()).hasStatus(403);
        assertThat(mvc.get().uri("/api/v1/programs/" + programId + "/pathway").with(user(t.signer())).exchange()).hasStatus(200);
    }

    @Test
    void retiringAGroupIsAuditedAndKeepsItsHistory() {
        var first = group(fx.pathwayGroup(t, programId, "2026-27", "Electives", "CHOOSE_COUNT", null, null, ids("COUN 650", "COUN 655", "COUN 645")), "Electives");
        UUID oldGroup = UUID.fromString(first.get("id").asString());

        var retired = fx.retirePathwayGroup(t, oldGroup, "The handbook says two of the three");
        assertThat(retired).hasStatus(200);
        assertThat(fx.read(retired).get("groups")).isEmpty();
        var replacement = fx.pathwayGroup(t, programId, "2026-27", "Electives: choose 2 of 3", "CHOOSE_COUNT", 2, null, ids("COUN 650", "COUN 655", "COUN 645"));
        assertThat(replacement.get("groups")).hasSize(1);

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(oldGroup));
        assertThat(entries).extracting(e -> e.action()).containsExactlyInAnyOrder("PATHWAY_GROUP_CREATED", "PATHWAY_GROUP_RETIRED");
        assertThat(entries).filteredOn(e -> e.action().equals("PATHWAY_GROUP_RETIRED")).singleElement()
                .satisfies(e -> assertThat(e.detail()).containsEntry("reason", "The handbook says two of the three").containsEntry("label", "Electives"));
        TenantContext.runAs(t.tenantId(), () -> {
            assertThat(jdbc.sql("SELECT count(*) FROM pathway_group WHERE program_id = :p").param("p", programId).query(Long.class).single()).isEqualTo(2);
            assertThat(jdbc.sql("SELECT count(*) FROM pathway_group_course WHERE group_id = :g").param("g", oldGroup).query(Long.class).single()).isEqualTo(3);
            assertThat(jdbc.sql("SELECT reason FROM pathway_group_retirement WHERE group_id = :g").param("g", oldGroup).query(String.class).single())
                    .isEqualTo("The handbook says two of the three");
            return null;
        });

        var again = fx.retirePathwayGroup(t, oldGroup, "Again");
        assertThat(again).hasStatus(409);
        assertThat(mvc.post().uri("/api/v1/pathway-groups/" + UUID.randomUUID() + "/retire").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}").exchange()).hasStatus(404);
    }

    @Test
    void creditsAreSetOnACourseVersionAndAnotherCatalogYearHasItsOwn() {
        fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, ids("COUN 605", "COUN 610"));
        var added = mvc.post().uri("/api/v1/courses/" + course.get("COUN 605") + "/versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("catalogYear", "2027-28", "required", true, "credits", "4", "creditUnit", "QUARTER")))
                .exchange();
        assertThat(added).hasStatus(201);
        var version = fx.read(added).get("versions").get(1);
        assertThat(version.get("credits").decimalValue()).isEqualByComparingTo("4");
        assertThat(version.get("creditUnit").asString()).isEqualTo("QUARTER");
        UUID courseId = course.get("COUN 605");
        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(courseId));
        assertThat(entries).filteredOn(e -> e.action().equals("COURSE_VERSION_ADDED")).singleElement()
                .satisfies(e -> assertThat(e.detail()).containsEntry("credits", "4").containsEntry("creditUnit", "QUARTER"));

        // The 2026-27 pathway still reads the credits of the 2026-27 version; there is no 2027-28 group yet.
        var year1 = pathway("?catalogYear=2026-27");
        assertThat(member(group(year1, "Required courses"), "COUN 605").get("credits").decimalValue()).isEqualByComparingTo("3");
        assertThat(year1.get("minimumCredits").decimalValue()).isEqualByComparingTo("9");
        fx.pathwayGroup(t, programId, "2027-28", "Required courses", "ALL_OF", null, null, ids("COUN 605", "COUN 610"));
        var year2 = pathway("?catalogYear=2027-28");
        assertThat(member(group(year2, "Required courses"), "COUN 605").get("credits").decimalValue()).isEqualByComparingTo("4");
        assertThat(member(group(year2, "Required courses"), "COUN 605").get("creditUnit").asString()).isEqualTo("QUARTER");
        assertThat(year2.get("minimumCredits").isNull()).isTrue();
        assertThat(year2.get("unknowns").get(0).asString()).startsWith("credits are recorded in more than one unit");
        assertThat(pathway("").get("catalogYear").asString()).isEqualTo("2026-27");
    }

    @Test
    void aCourseWithoutCreditsHasUnknownCreditsAndAUnitOfSemesters() {
        fx.courseOnly(t, programId, "COUN 700", true, null);
        var courseId = fx.courseIds(t, programId).get("COUN 700");
        fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, List.of(courseId));
        var view = pathway("");
        assertThat(member(group(view, "Required courses"), "COUN 700").get("credits").isNull()).isTrue();
        assertThat(view.get("minimumCredits").isNull()).isTrue();
        assertThat(view.get("unknowns").get(0).asString()).isEqualTo("Required courses: credits not recorded for COUN 700");
    }

    @Test
    void anotherTenantCannotReadOrRetireAGroup() {
        var made = group(fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, ids("COUN 605")), "Required courses");
        var other = tenants.create("pathway-other");
        assertThat(mvc.get().uri("/api/v1/programs/" + programId + "/pathway").with(user(other.admin())).exchange()).hasStatus(404);
        assertThat(mvc.post().uri("/api/v1/pathway-groups/" + made.get("id").asString() + "/retire").with(user(other.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}").exchange()).hasStatus(404);
        assertThat(pathway("").get("groups")).hasSize(1);
    }
}
