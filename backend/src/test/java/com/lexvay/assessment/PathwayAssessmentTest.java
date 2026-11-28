package com.lexvay.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * What the graduation paths change in an assessment (AC-1): a quote from a course that is not on every path is ambiguous,
 * whatever the pathway leaves unrecorded counts against it, and a total of credits is checked against the pathway.
 */
class PathwayAssessmentTest extends AbstractAssessmentTest {

    static final String QUOTE = "the etiology of substance use disorders, screening and assessment of addictions,\nand evidence-based treatment of addictive behaviors";

    UUID programId;
    UUID syllabus650;

    @BeforeEach
    void setUp() throws Exception {
        programId = fx.program(t, List.of("UT"));
        fx.course(t, programId, "COUN 605", true, "3");
        UUID cv650 = fx.course(t, programId, "COUN 650", false, "3");
        fx.course(t, programId, "COUN 655", false, "3");
        fx.course(t, programId, "COUN 645", false, "3");
        syllabus650 = fx.document(t, programId, "coun650.txt", "SYLLABUS", cv650, SYLLABUS_650);
        fx.clearPathway(t, programId);
    }

    private List<UUID> courses(String... codes) {
        var ids = fx.courseIds(t, programId);
        return java.util.Arrays.stream(codes).map(ids::get).toList();
    }

    /** Runs the addictions requirement, which the scripted model answers EVIDENCE_FOUND from the COUN 650 syllabus. */
    private JsonNode addictions() throws Exception {
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah pathway", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Addictions", "coursework in addictions and substance use disorders"))));
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", syllabus650, 1, QUOTE, "The course covers addictions directly."));
        return fx.mappingFor(fx.mappings(t, fx.runAssessment(t, programId), "UT"), "UT-1");
    }

    @Test
    void anElectiveCreatedThroughTheApiIsAmbiguousUntilThePathwayRecordsTheMinimum() throws Exception {
        programId = fx.program(t, List.of("UT"));
        fx.course(t, programId, "COUN 605", true, "3");
        UUID cv650 = fx.course(t, programId, "COUN 650", false, "3");
        syllabus650 = fx.document(t, programId, "coun650-api.txt", "SYLLABUS", cv650, SYLLABUS_650);

        var mapping = addictions();

        assertThat(mapping.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(mapping.get("rationale").asString())
                .contains("the program's graduation paths are not fully known (Electives: minimum not recorded).");
    }

    @Test
    void aRequiredCourseCreatedThroughTheApiIsOnEveryPath() throws Exception {
        programId = fx.program(t, List.of("UT"));
        UUID cv605 = fx.course(t, programId, "COUN 605", true, "3");
        UUID syllabus = fx.document(t, programId, "coun605-api.txt", "SYLLABUS", cv605, SYLLABUS_605);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah pathway required", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"))));
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1,
                "legal standards, confidentiality, duty to warn,\nand professional identity of clinical mental health counselors.", "Ethics content."));

        var mapping = fx.mappingFor(fx.mappings(t, fx.runAssessment(t, programId), "UT"), "UT-1");

        assertThat(mapping.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
    }

    @Test
    void aQuoteFromAnElectiveOfChooseTwoOfThreeIsAmbiguous() throws Exception {
        fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, courses("COUN 605"));
        fx.pathwayGroup(t, programId, "2026-27", "Electives: choose 2 of 3", "CHOOSE_COUNT", 2, null, courses("COUN 650", "COUN 655", "COUN 645"));

        var mapping = addictions();

        assertThat(mapping.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(mapping.get("rationale").asString()).contains("Downgraded to ambiguous: the cited course COUN 650 is not on every graduation path.");
        assertThat(mapping.get("evidence").get("quote").asString()).startsWith("the etiology of substance use disorders");
    }

    @Test
    void aQuoteFromAnElectiveOfChooseThreeOfThreeIsNotDowngraded() throws Exception {
        fx.pathwayGroup(t, programId, "2026-27", "Electives: choose 3 of 3", "CHOOSE_COUNT", 3, null, courses("COUN 650", "COUN 655", "COUN 645"));

        var mapping = addictions();

        assertThat(mapping.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(mapping.get("rationale").asString()).doesNotContain("Downgraded");
    }

    @Test
    void anUnknownMinimumMakesTheQuoteAmbiguousAndNamesWhatIsNotRecorded() throws Exception {
        fx.pathwayGroup(t, programId, "2026-27", "Electives", "CHOOSE_COUNT", null, null, courses("COUN 650", "COUN 655", "COUN 645"));

        var mapping = addictions();

        assertThat(mapping.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(mapping.get("rationale").asString())
                .contains("Downgraded to ambiguous: the program's graduation paths are not fully known (Electives: minimum not recorded).");
    }

    @Test
    void aCourseNoGroupPlacesMakesTheQuoteAmbiguousAndSaysSo() throws Exception {
        fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, courses("COUN 605"));

        var mapping = addictions();

        assertThat(mapping.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(mapping.get("rationale").asString()).contains("(COUN 650 is not placed in a graduation group)");
    }

    @Test
    void aRetiredGroupNoLongerCountsAndItsReplacementDoes() throws Exception {
        var first = fx.pathwayGroup(t, programId, "2026-27", "Electives", "CHOOSE_COUNT", 3, null, courses("COUN 650", "COUN 655", "COUN 645"));
        UUID groupId = UUID.fromString(first.get("groups").get(0).get("id").asString());
        assertThat(fx.retirePathwayGroup(t, groupId, "The handbook asks for two")).hasStatus(200);
        fx.pathwayGroup(t, programId, "2026-27", "Electives: choose 2 of 3", "CHOOSE_COUNT", 2, null, courses("COUN 650", "COUN 655", "COUN 645"));

        assertThat(addictions().get("finding").asString()).isEqualTo("AMBIGUOUS");
    }

    @Test
    void aTotalOfCreditsThatEqualsThePathwayMinimumIsNotDowngraded() throws Exception {
        // 3 + 3 + 3 = 9 credits: the required course and the cheapest two of three electives.
        assertThat(totalCredits("9", "at least 9 semester credit hours", 9, 9).get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
    }

    @Test
    void aTotalOfCreditsThatDiffersFromThePathwayMinimumIsAmbiguousWithBothNumbersAndTheirSources() throws Exception {
        var mapping = totalCredits("60", "at least 60 semester credit hours", 60, 9);

        assertThat(mapping.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(mapping.get("method").asString()).isEqualTo("RULES_ENGINE");
        assertThat(mapping.get("rationale").asString()).startsWith("TOTAL_CREDITS 60 >= 60 → true. Downgraded to ambiguous: ")
                .contains("the program total of 60 (degree-plan.txt, page 1)")
                .contains("differs from the pathway minimum of 9 semester credits (the credits of the courses in Required courses; Electives: choose 2 of 3)");
        assertThat(mapping.get("evidence").get("quote").asString()).isEqualTo("a total of 60 semester credit hours");
    }

    @Test
    void aTotalOfCreditsIsLeftAloneWhileThePathwayMinimumIsUnknown() throws Exception {
        UUID plan = fx.document(t, programId, "degree-plan.txt", "DEGREE_PLAN", null, DEGREE_PLAN);
        assertThat(fx.fact(t, programId, "TOTAL_CREDITS", "60", plan, 1, "a total of 60 semester credit hours")).hasStatus(201);
        fx.pathwayGroup(t, programId, "2026-27", "Electives", "CHOOSE_COUNT", null, null, courses("COUN 650", "COUN 655", "COUN 645"));
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah credits", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "at least 60 semester hours"))));

        var mapping = fx.mappingFor(fx.mappings(t, fx.runAssessment(t, programId), "UT"), "UT-1");

        assertThat(mapping.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(mapping.get("rationale").asString()).isEqualTo("TOTAL_CREDITS 60 >= 60 → true");
    }

    /** The total of credits requirement, with the fact the person recorded and the pathway whose minimum is {@code minimum}. */
    private JsonNode totalCredits(String factValue, String requirementText, int required, int minimum) throws Exception {
        UUID plan = fx.document(t, programId, "degree-plan.txt", "DEGREE_PLAN", null,
                factValue.equals("60") ? DEGREE_PLAN : "Fictional. Prototype data. The program requires a total of " + factValue + " semester credit hours.");
        String quote = "a total of " + factValue + " semester credit hours";
        assertThat(fx.fact(t, programId, "TOTAL_CREDITS", factValue, plan, 1, quote)).hasStatus(201);
        fx.pathwayGroup(t, programId, "2026-27", "Required courses", "ALL_OF", null, null, courses("COUN 605"));
        fx.pathwayGroup(t, programId, "2026-27", "Electives: choose 2 of 3", "CHOOSE_COUNT", 2, null, courses("COUN 650", "COUN 655", "COUN 645"));
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah credits", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", required, requirementText))));
        assertThat(fx.pathway(t, programId).get("minimumCredits").decimalValue()).isEqualByComparingTo(String.valueOf(minimum));
        return fx.mappingFor(fx.mappings(t, fx.runAssessment(t, programId), "UT"), "UT-1");
    }
}
