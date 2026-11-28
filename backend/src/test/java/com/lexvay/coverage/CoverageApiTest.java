package com.lexvay.coverage;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.auth.LexvayUser;
import com.lexvay.review.ReviewFixtures;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** The coverage matrix on small scenarios: evidence responses, rule and course changes, errors and tenancy. */
class CoverageApiTest extends AbstractAssessmentTest {

    static final String WEEKLY_SCHEDULE = """
            Fictional. Prototype data. COUN 605 weekly schedule.
            Week 1: The ACA Code of Ethics and professional identity.
            Week 2: Legal standards, informed consent and confidentiality.
            """;

    static final String HANDBOOK = """
            Fictional. Prototype data. Clinical handbook.
            Students complete an ethics orientation covering telehealth practice before the practicum begins.
            """;

    ReviewFixtures rf;
    ReviewFixtures.Scenario s;

    @BeforeEach
    void scenario() {
        rf = new ReviewFixtures(mvc, json, fx);
        s = rf.californiaScenario(t, "coverage " + UUID.randomUUID().toString().substring(0, 6));
    }

    private JsonNode matrix(LexvayUser as, UUID programId, String jurisdiction) {
        var result = mvc.get().uri("/api/v1/programs/" + programId + "/coverage?jurisdiction=" + jurisdiction).with(user(as)).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result);
    }

    private JsonNode matrix() {
        return matrix(t.reviewer(), s.programId(), "CA");
    }

    private static JsonNode row(JsonNode matrix, String ref) {
        for (JsonNode requirement : matrix.get("requirements")) {
            if (requirement.get("ref").asString().equals(ref)) {
                return requirement;
            }
        }
        throw new AssertionError("No row for " + ref);
    }

    private static List<String> cells(JsonNode requirement) {
        return requirement.get("cells").valueStream()
                .map(c -> c.get("courseVersionId").asString() + " " + c.get("source").asString() + " " + c.get("quoteCount").asInt()).toList();
    }

    private void respond(UUID mappingId, UUID documentId, String quote) {
        assertThat(rf.review(t.reviewer(), mappingId, "EVIDENCE_REQUESTED", "Please send supporting evidence.")).hasStatus(201);
        Map<String, Object> body = new HashMap<>();
        body.put("documentId", documentId.toString());
        body.put("pageNumber", 1);
        body.put("quote", quote);
        var created = mvc.post().uri("/api/v1/mappings/" + mappingId + "/evidence-responses").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
        assertThat(created).hasStatus(201);
    }

    @Test
    void listsEveryRequirementInOrderWithItsCellsAndRisk() {
        JsonNode m = matrix(t.contributor(), s.programId(), "CA");

        assertThat(m.get("program").get("id").asString()).isEqualTo(s.programId().toString());
        assertThat(m.get("jurisdiction").get("code").asString()).isEqualTo("CA");
        assertThat(m.get("jurisdiction").get("name").asString()).isNotBlank();
        assertThat(m.get("assessmentId").asString()).isEqualTo(s.assessmentId().toString());
        assertThat(m.get("ruleVersion").get("id").asString()).isEqualTo(s.ruleVersionId().toString());
        assertThat(m.get("ruleVersion").get("label").asString()).startsWith("Demo rule set California");

        assertThat(m.get("courses")).extracting(c -> c.get("code").asString()).as("required first, then by code")
                .containsExactly("COUN 605", "COUN 650");
        JsonNode first = m.get("courses").get(0);
        assertThat(first.get("courseVersionId").asString()).isEqualTo(s.course605Version().toString());
        assertThat(first.get("catalogYear").asString()).isNotBlank();
        assertThat(first.get("title").asString()).isNotBlank();
        assertThat(first.get("courseId").asString()).isNotBlank();
        assertThat(first.get("required").asBoolean()).isTrue();
        assertThat(m.get("courses").get(1).get("required").asBoolean()).isFalse();

        assertThat(m.get("requirements")).extracting(r -> r.get("ref").asString()).containsExactly("CA-1", "CA-2", "CA-3");
        JsonNode credits = row(m, "CA-1");
        assertThat(credits.get("mappingId").asString()).isEqualTo(s.foundMapping().toString());
        assertThat(credits.get("type").asString()).isEqualTo("NUMERIC");
        assertThat(credits.get("cells")).as("a number checked by the rules engine cites no course").isEmpty();
        assertThat(credits.get("programLevel")).isEmpty();
        assertThat(credits.get("risk").asString()).isEqualTo("NONE");
        assertThat(credits.get("decision").isNull()).isTrue();
        assertThat(credits.get("outdatedCells")).isEmpty();

        JsonNode telehealth = row(m, "CA-2");
        assertThat(telehealth.get("mappingId").asString()).isEqualTo(s.missingMapping().toString());
        assertThat(telehealth.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(telehealth.get("cells")).isEmpty();
        assertThat(telehealth.get("risk").asString()).isEqualTo("NO_EVIDENCE");

        JsonNode ethics = row(m, "CA-3");
        assertThat(ethics.get("mappingId").asString()).isEqualTo(s.ambiguousMapping().toString());
        assertThat(ethics.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(cells(ethics)).containsExactly(s.course605Version() + " SYSTEM 1");
        assertThat(ethics.get("programLevel")).isEmpty();
        assertThat(ethics.get("risk").asString()).isEqualTo("SINGLE_COURSE");
    }

    @Test
    void reportsWhatPeopleDecidedAndNothingElse() {
        rf.decideAll(t, s);

        JsonNode m = matrix();
        assertThat(row(m, "CA-1").get("decision").asString()).isEqualTo("ACCEPTED");
        assertThat(row(m, "CA-2").get("decision").asString()).isEqualTo("GAP_CONFIRMED");
        assertThat(row(m, "CA-2").get("finding").asString()).as("a person's decision never rewrites the proposal").isEqualTo("EVIDENCE_MISSING");
        assertThat(row(m, "CA-3").get("decision").asString()).isEqualTo("EVIDENCE_REQUESTED");
        assertThat(m.get("requirements")).extracting(r -> r.get("finding").asString())
                .allMatch(f -> List.of("EVIDENCE_FOUND", "EVIDENCE_MISSING", "AMBIGUOUS").contains(f));
    }

    @Test
    void anEvidenceResponseAddsAPersonCellBesideTheSystemCell() {
        UUID schedule = fx.document(t, s.programId(), "coun605-schedule.txt", "SYLLABUS", s.course605Version(), WEEKLY_SCHEDULE);
        respond(s.ambiguousMapping(), schedule, "Week 1: The ACA Code of Ethics and professional identity.");

        JsonNode ethics = row(matrix(), "CA-3");
        assertThat(cells(ethics)).containsExactly(s.course605Version() + " SYSTEM 1", s.course605Version() + " PERSON 1");
        assertThat(ethics.get("finding").asString()).as("the response never changes the proposal").isEqualTo("AMBIGUOUS");
        assertThat(ethics.get("decision").isNull()).as("the response reopened the requirement").isTrue();
        assertThat(ethics.get("risk").asString()).isEqualTo("SINGLE_COURSE");
    }

    @Test
    void aResponseInAProgramDocumentIsProgramLevelEvidence() {
        UUID handbook = fx.document(t, s.programId(), "clinical-handbook.txt", "HANDBOOK", null, HANDBOOK);
        respond(s.missingMapping(), handbook, "Students complete an ethics orientation covering telehealth practice");

        JsonNode telehealth = row(matrix(), "CA-2");
        assertThat(telehealth.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(telehealth.get("cells")).isEmpty();
        assertThat(telehealth.get("programLevel")).hasSize(1);
        JsonNode document = telehealth.get("programLevel").get(0);
        assertThat(document.get("documentId").asString()).isEqualTo(handbook.toString());
        assertThat(document.get("filename").asString()).isEqualTo("clinical-handbook.txt");
        assertThat(document.get("kind").asString()).isEqualTo("HANDBOOK");
        assertThat(document.get("source").asString()).isEqualTo("PERSON");
        assertThat(telehealth.get("risk").asString()).isEqualTo("NONE");
    }

    @Test
    void evidenceOnlyInAnElectiveWhoseMinimumIsNotRecordedIsFlaggedAsNotFullyKnown() {
        UUID syllabus650 = fx.document(t, s.programId(), "coun650.txt", "SYLLABUS", s.course650Version(), SYLLABUS_650);
        respond(s.missingMapping(), syllabus650, "etiology of substance use disorders, screening and assessment of addictions");

        JsonNode telehealth = row(matrix(), "CA-2");
        assertThat(cells(telehealth)).containsExactly(s.course650Version() + " PERSON 1");
        assertThat(telehealth.get("risk").asString()).isEqualTo("PATHS_UNKNOWN");
    }

    /** Replaces the electives group of the scenario by "choose 1 of 2" with a second elective, COUN 655. */
    private UUID chooseOneOfTwoElectives() {
        fx.course(t, s.programId(), "COUN 655", false, "3");
        var ids = fx.courseIds(t, s.programId());
        fx.pathway(t, s.programId()).get("groups").forEach(g -> {
            if (g.get("label").asString().equals("Electives")) {
                assertThat(fx.retirePathwayGroup(t, UUID.fromString(g.get("id").asString()), "The handbook offers a second elective")).hasStatus(200);
            }
        });
        fx.pathwayGroup(t, s.programId(), "2026-27", "Electives: choose 1 of 2", "CHOOSE_COUNT", 1, null, List.of(ids.get("COUN 650"), ids.get("COUN 655")));
        return fx.document(t, s.programId(), "coun655.txt", "SYLLABUS",
                fx.courseVersionOf(t, s.programId(), "COUN 655"), "Fictional. Prototype data. COUN 655 Psychopharmacology. The course covers medication classes and their effects.");
    }

    @Test
    void evidenceOnlyInAnElectiveOfAKnownChoiceIsFlaggedAsRestingOnAnElective() {
        UUID syllabus650 = fx.document(t, s.programId(), "coun650.txt", "SYLLABUS", s.course650Version(), SYLLABUS_650);
        chooseOneOfTwoElectives();
        respond(s.missingMapping(), syllabus650, "etiology of substance use disorders, screening and assessment of addictions");

        JsonNode telehealth = row(matrix(), "CA-2");
        assertThat(cells(telehealth)).containsExactly(s.course650Version() + " PERSON 1");
        assertThat(telehealth.get("risk").asString()).isEqualTo("ELECTIVE_ONLY");
    }

    @Test
    void evidenceInEveryElectiveOfAChoiceIsOnEveryPathAndNotFlagged() {
        UUID syllabus650 = fx.document(t, s.programId(), "coun650.txt", "SYLLABUS", s.course650Version(), SYLLABUS_650);
        UUID syllabus655 = chooseOneOfTwoElectives();
        respond(s.missingMapping(), syllabus650, "etiology of substance use disorders, screening and assessment of addictions");
        respond(s.missingMapping(), syllabus655, "covers medication classes and their effects");

        JsonNode telehealth = row(matrix(), "CA-2");
        assertThat(telehealth.get("cells")).hasSize(2);
        assertThat(telehealth.get("risk").asString()).isEqualTo("NONE");
    }

    @Test
    void evidenceInARequiredAndAnElectiveCourseIsNotFlagged() {
        UUID syllabus650 = fx.document(t, s.programId(), "coun650.txt", "SYLLABUS", s.course650Version(), SYLLABUS_650);
        respond(s.ambiguousMapping(), syllabus650, "etiology of substance use disorders, screening and assessment of addictions");

        JsonNode ethics = row(matrix(), "CA-3");
        assertThat(cells(ethics)).containsExactly(s.course605Version() + " SYSTEM 1", s.course650Version() + " PERSON 1");
        assertThat(ethics.get("risk").asString()).isEqualTo("NONE");
    }

    @Test
    void aSupersededRuleVersionDisappearsOnlyAfterTheRerun() {
        rf.decideAll(t, s);
        JsonNode before = matrix();
        String revised = fx.requirements("CA", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 66, "at least 66 semester hours"),
                AssessmentFixtures.specific("Telehealth", "training in telehealth practice"),
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards")));
        String label = "Demo rule set California coverage rev 2";
        fake.script("EXTRACT:" + label, revised);
        var created = mvc.post().uri("/api/v1/rule-versions").with(user(t.contributor())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jurisdiction", "CA", "licenseType", AssessmentFixtures.LICENSE, "label", label,
                        "sourceUrl", "https://example.invalid/coverage-rev-2", "effectiveFrom", LocalDate.of(2027, 1, 1).toString(),
                        "text", "Fictional revised rule text", "supersedesId", s.ruleVersionId().toString()))).exchange();
        assertThat(created).hasStatus(202);
        UUID revision = UUID.fromString(fx.read(created).get("ruleVersionId").asString());
        runner.runPending();

        JsonNode waiting = matrix();
        assertThat(waiting.get("ruleVersion").get("id").asString()).as("the old list stays until the revision is approved and re-run")
                .isEqualTo(s.ruleVersionId().toString());
        assertThat(waiting.get("requirements")).hasSize(3);
        assertThat(row(waiting, "CA-1").get("risk").asString()).as("a number checked by the rules engine is never described").isEqualTo("NONE");
        assertThat(row(waiting, "CA-2").get("risk").asString()).as("a stale mapping is outdated, whatever it cites").isEqualTo("OUTDATED_EVIDENCE");
        assertThat(row(waiting, "CA-3").get("risk").asString()).isEqualTo("OUTDATED_EVIDENCE");

        var events = fx.read(mvc.get().uri("/api/v1/change-events").with(user(t.reviewer())).exchange());
        UUID eventId = UUID.fromString(events.get(0).get("id").asString());
        assertThat(mvc.post().uri("/api/v1/rule-versions/" + revision + "/requirements/approve").with(user(t.reviewer())).with(csrf()).exchange()).hasStatus(200);
        assertThat(mvc.post().uri("/api/v1/change-events/" + eventId + "/rerun").with(user(t.reviewer())).with(csrf()).exchange()).hasStatus(202);
        runner.runPending();

        JsonNode after = matrix();
        assertThat(after.get("ruleVersion").get("id").asString()).isEqualTo(revision.toString());
        assertThat(after.get("ruleVersion").get("label").asString()).isEqualTo(label);
        assertThat(after.get("assessmentId")).isEqualTo(before.get("assessmentId"));
        assertThat(after.get("requirements")).hasSize(3).noneMatch(r -> r.get("risk").asString().equals("OUTDATED_EVIDENCE"));
        List<String> oldMappings = before.get("requirements").valueStream().map(r -> r.get("mappingId").asString()).toList();
        assertThat(after.get("requirements")).extracting(r -> r.get("mappingId").asString()).doesNotContainAnyElementsOf(oldMappings);
        assertThat(row(after, "CA-1").get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
    }

    @Test
    void evidenceInADocumentOfASupersededCourseVersionIsNotShownAsACell() {
        JsonNode courses = fx.read(mvc.get().uri("/api/v1/programs/" + s.programId() + "/courses").with(user(t.reviewer())).exchange());
        String courseId605 = null;
        for (JsonNode course : courses) {
            if (course.get("code").asString().equals("COUN 605")) {
                courseId605 = course.get("id").asString();
            }
        }
        var added = mvc.post().uri("/api/v1/courses/" + courseId605 + "/versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("catalogYear", "2027-28", "required", true))).exchange();
        assertThat(added).hasStatus(201);
        String newVersion = fx.read(added).get("versions").get(1).get("id").asString();

        JsonNode m = matrix();
        assertThat(m.get("courses")).extracting(c -> c.get("courseVersionId").asString())
                .as("only the current version of each course is a column").containsExactly(newVersion, s.course650Version().toString());
        JsonNode ethics = row(m, "CA-3");
        assertThat(ethics.get("cells")).isEmpty();
        assertThat(ethics.get("outdatedCells")).hasSize(1);
        JsonNode outdated = ethics.get("outdatedCells").get(0);
        assertThat(outdated.get("courseVersionId").asString()).isEqualTo(s.course605Version().toString());
        assertThat(outdated.get("code").asString()).isEqualTo("COUN 605");
        assertThat(outdated.get("catalogYear").asString()).isEqualTo("2026-27");
        assertThat(outdated.get("supersededBy").asString()).isEqualTo(newVersion);
        assertThat(outdated.get("source").asString()).isEqualTo("SYSTEM");
        assertThat(outdated.get("quoteCount").asInt()).isEqualTo(1);
        assertThat(ethics.get("risk").asString()).as("evidence exists, on an outdated version").isEqualTo("OUTDATED_EVIDENCE");
        assertThat(row(m, "CA-2").get("risk").asString()).as("a mapping that cites nothing is not outdated by the course change")
                .isEqualTo("NO_EVIDENCE");
    }

    @Test
    void aProgramWithoutAFinishedAssessmentHasCoursesAndNoRows() {
        UUID program = fx.program(t, List.of("CA"));
        UUID courseVersion = fx.course(t, program, "COUN 605", true);

        JsonNode m = matrix(t.reviewer(), program, "CA");
        assertThat(m.get("assessmentId").isNull()).isTrue();
        assertThat(m.get("ruleVersion").isNull()).isTrue();
        assertThat(m.get("requirements")).isEmpty();
        assertThat(m.get("courses")).extracting(c -> c.get("courseVersionId").asString()).containsExactly(courseVersion.toString());
    }

    private MvcTestResult get(LexvayUser as, UUID programId, String query) {
        return mvc.get().uri("/api/v1/programs/" + programId + "/coverage" + query).with(user(as)).exchange();
    }

    @Test
    void aJurisdictionThatIsNotATargetOfTheProgramIsRefused() {
        assertThat(get(t.reviewer(), s.programId(), "?jurisdiction=TX")).hasStatus(400);
        assertThat(get(t.reviewer(), s.programId(), "?jurisdiction=ZZ")).as("a code that is no jurisdiction").hasStatus(400);
        assertThat(get(t.reviewer(), s.programId(), "?jurisdiction=ca")).hasStatus(400);
        assertThat(get(t.reviewer(), s.programId(), "")).as("the jurisdiction is required").hasStatus(400);
        assertThat(get(t.reviewer(), s.programId(), "?jurisdiction=UT")).as("UT is a target with a rule set but no coverage yet").hasStatus(200);
    }

    @Test
    void anotherTenantAndAnUnknownProgramAreNotFound() {
        var other = tenants.create("other");
        assertThat(get(other.admin(), s.programId(), "?jurisdiction=CA")).hasStatus(404);
        assertThat(get(t.reviewer(), UUID.randomUUID(), "?jurisdiction=CA")).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/programs/" + s.programId() + "/coverage?jurisdiction=CA").exchange()).hasStatus(401);
    }
}
