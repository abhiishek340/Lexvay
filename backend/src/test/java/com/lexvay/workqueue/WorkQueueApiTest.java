package com.lexvay.workqueue;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.auth.LexvayUser;
import com.lexvay.common.TenantContext;
import com.lexvay.review.ReviewFixtures;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

class WorkQueueApiTest extends AbstractAssessmentTest {

    static final String ETHICS_SCHEDULE = """
            Fictional. Prototype data. COUN 605 weekly schedule.
            Week 1: The ACA Code of Ethics and professional identity.
            Week 2: Legal standards, informed consent and confidentiality.
            """;

    @Autowired JdbcClient jdbc;

    ReviewFixtures rf;

    @BeforeEach
    void fixtures() {
        rf = new ReviewFixtures(mvc, json, fx);
    }

    private ReviewFixtures.Scenario california() {
        return rf.californiaScenario(t, "queue " + UUID.randomUUID().toString().substring(0, 6));
    }

    // --- reading the queue ---

    private JsonNode queue(LexvayUser as) {
        var result = mvc.get().uri("/api/v1/work-queue").with(user(as)).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result);
    }

    private long total(LexvayUser as) {
        var result = mvc.get().uri("/api/v1/work-queue/count").with(user(as)).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result).get("total").asLong();
    }

    private static JsonNode item(JsonNode queue, String kind, String jurisdiction) {
        for (JsonNode item : queue.get("items")) {
            boolean sameJurisdiction = jurisdiction == null ? item.get("jurisdictionCode").isNull()
                    : jurisdiction.equals(item.get("jurisdictionCode").asString());
            if (item.get("kind").asString().equals(kind) && sameJurisdiction) {
                return item;
            }
        }
        return null;
    }

    private static long count(JsonNode queue, String kind, String jurisdiction) {
        JsonNode item = item(queue, kind, jurisdiction);
        return item == null ? 0 : item.get("count").asLong();
    }

    private static List<String> kinds(JsonNode queue) {
        Set<String> kinds = new LinkedHashSet<>();
        queue.get("items").forEach(item -> kinds.add(item.get("kind").asString()));
        return new ArrayList<>(kinds);
    }

    private static List<String> ids(JsonNode item, String field) {
        List<String> ids = new ArrayList<>();
        item.get("ids").get(field).forEach(id -> ids.add(id.asString()));
        return ids;
    }

    /** The badge total is the sum of the counts, whoever asks. */
    private void assertTotalAgreesWithTheList(LexvayUser as) {
        JsonNode queue = queue(as);
        long sum = 0;
        for (JsonNode item : queue.get("items")) {
            sum += item.get("count").asLong();
        }
        long byKind = 0;
        for (JsonNode n : queue.get("counts")) {
            byKind += n.asLong();
        }
        assertThat(byKind).as("counts add up to the items").isEqualTo(sum);
        assertThat(total(as)).as("%s total", as.role()).isEqualTo(sum);
    }

    // --- acting ---

    private void review(UUID mapping, String kind, String note) {
        assertThat(rf.review(t.reviewer(), mapping, kind, note)).hasStatus(201);
    }

    private void answer(UUID mapping, UUID document, String quote) {
        var body = Map.of("documentId", document.toString(), "pageNumber", 1, "quote", quote);
        assertThat(mvc.post().uri("/api/v1/mappings/" + mapping + "/evidence-responses").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange()).hasStatus(201);
    }

    /** One scripted model answer per extraction attempt: a valid list is extracted at once, two invalid ones fail the job. */
    private UUID createRuleVersion(String label, String jurisdiction, String... modelAnswers) {
        fake.script("EXTRACT:" + label, modelAnswers);
        var created = mvc.post().uri("/api/v1/rule-versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jurisdiction", jurisdiction, "licenseType", AssessmentFixtures.LICENSE,
                        "label", label, "sourceUrl", "https://example.invalid/" + jurisdiction, "effectiveFrom", "2027-01-01", "legalStatus", "IN_FORCE", "datesBasis", "Fictional test data",
                        "text", "Fictional rule text for " + label)))
                .exchange();
        assertThat(created).hasStatus(202);
        UUID id = UUID.fromString(fx.read(created).get("ruleVersionId").asString());
        runner.runPending();
        return id;
    }

    private String oneRequirement(String jurisdiction) {
        return fx.requirements(jurisdiction, List.of(AssessmentFixtures.subject("Ethics", "coursework in professional ethics")));
    }

    private UUID courseId(UUID programId, String code) {
        for (JsonNode course : fx.read(mvc.get().uri("/api/v1/programs/" + programId + "/courses").with(user(t.reviewer())).exchange())) {
            if (course.get("code").asString().equals(code)) {
                return UUID.fromString(course.get("id").asString());
            }
        }
        throw new AssertionError("No course " + code);
    }

    /** Returns the new course version id. */
    private UUID addCourseVersion(UUID courseId, String year) {
        var added = mvc.post().uri("/api/v1/courses/" + courseId + "/versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("catalogYear", year, "required", true)))
                .exchange();
        assertThat(added).hasStatus(201);
        JsonNode versions = fx.read(added).get("versions");
        return UUID.fromString(versions.get(versions.size() - 1).get("id").asString());
    }

    private void markStale(UUID mapping) {
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("UPDATE mapping SET stale = true WHERE id = :id").param("id", mapping).update());
    }

    /**
     * A second program of the same tenant for California and Utah, assessed against the approved rule versions the
     * first scenario created (identical rule text cannot be uploaded twice in one tenant).
     */
    private ReviewFixtures.Scenario anotherProgram(ReviewFixtures.Scenario first) {
        UUID programId = fx.program(t, List.of("CA", "UT"));
        UUID course = fx.course(t, programId, "COUN 605", true);
        UUID syllabus = fx.document(t, programId, "coun605.txt", "SYLLABUS", course, SYLLABUS_605);
        UUID plan = fx.document(t, programId, "degree-plan.txt", "DEGREE_PLAN", null, DEGREE_PLAN);
        assertThat(fx.fact(t, programId, "TOTAL_CREDITS", "60", plan, 1, "a total of 60 semester credit hours")).hasStatus(201);
        fake.reset();
        fx.fakeScript("MATCH:CA-2", fx.matchOutput("EVIDENCE_MISSING", null, null, null, "Nothing mentions telehealth."));
        fx.fakeScript("MATCH:CA-3", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1, "ACA Code of Ethics", "Names the code only."));
        UUID assessmentId = fx.runAssessment(t, programId);
        JsonNode mappings = fx.mappings(t, assessmentId, "CA");
        return new ReviewFixtures.Scenario(programId, assessmentId, mappingId(mappings, "CA-1"), mappingId(mappings, "CA-2"),
                mappingId(mappings, "CA-3"), first.ruleVersionId(), course, null);
    }

    /** The evidence request of the scenario's ethics mapping, as the program's request list shows it. */
    private JsonNode evidenceRequest(ReviewFixtures.Scenario s) {
        var result = mvc.get().uri("/api/v1/programs/" + s.programId() + "/evidence-requests?status=ALL").with(user(t.contributor())).exchange();
        assertThat(result).hasStatus(200);
        JsonNode requests = fx.read(result);
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("mappingId").asString()).isEqualTo(s.ambiguousMapping().toString());
        return requests.get(0);
    }

    private UUID mappingId(JsonNode mappings, String ref) {
        return UUID.fromString(fx.mappingFor(mappings, ref).get("id").asString());
    }

    private UUID utMapping(ReviewFixtures.Scenario s) {
        return UUID.fromString(fx.mappings(t, s.assessmentId(), "UT").get(0).get("id").asString());
    }

    // --- tests ---

    @Test
    void everyRoleSeesExactlyItsKindsInTheListedOrder() throws Exception {
        var s = california();
        // CA: three undecided, then one evidence request. UT: its only mapping accepted, so UT is ready to sign.
        review(s.ambiguousMapping(), "EVIDENCE_REQUESTED", "Please send the schedule.");
        review(utMapping(s), "ACCEPTED", null);
        UUID approvable = createRuleVersion("Demo rule set Texas awaiting approval", "TX", oneRequirement("TX"));
        UUID broken = createRuleVersionWhoseExtractionFails("Demo rule set Arizona broken", "AZ");
        addCourseVersion(courseId(s.programId(), "COUN 650"), "2027-28");
        UUID failedProgram = failedAssessmentProgram("CO");

        JsonNode contributor = queue(t.contributor());
        JsonNode reviewer = queue(t.reviewer());
        JsonNode signer = queue(t.signer());
        JsonNode admin = queue(t.admin());

        assertThat(kinds(contributor)).containsExactly("EVIDENCE_REQUESTS_OPEN");
        assertThat(kinds(reviewer)).containsExactly("REQUIREMENTS_TO_REVIEW", "EVIDENCE_REQUESTS_OPEN",
                "RULE_VERSIONS_TO_APPROVE", "EXTRACTION_FAILED", "CHANGES_OPEN");
        assertThat(kinds(signer)).containsExactly("REQUIREMENTS_TO_REVIEW", "EVIDENCE_REQUESTS_OPEN",
                "RULE_VERSIONS_TO_APPROVE", "CHANGES_OPEN", "READY_TO_SIGN");
        assertThat(kinds(admin)).containsExactly("EVIDENCE_REQUESTS_OPEN", "EXTRACTION_FAILED", "CHANGES_OPEN", "ASSESSMENTS_FAILED");
        for (LexvayUser as : List.of(t.contributor(), t.reviewer(), t.signer(), t.admin())) {
            assertTotalAgreesWithTheList(as);
            assertThat(queue(as).get("generatedAt").asString()).isNotBlank();
        }
        assertThat(contributor.get("items")).as("a contributor never sees ready-to-sign").noneMatch(i -> i.get("kind").asString().equals("READY_TO_SIGN"));

        // What each item carries.
        JsonNode review = item(signer, "REQUIREMENTS_TO_REVIEW", "CA");
        assertThat(review.get("programId").asString()).isEqualTo(s.programId().toString());
        assertThat(review.get("programName").asString()).isEqualTo("M.S. Clinical Mental Health Counseling");
        assertThat(review.get("count").asLong()).isEqualTo(2);
        assertThat(review.get("oldestAt").asString()).isNotBlank();
        assertThat(review.get("ids").get("assessmentId").asString()).isEqualTo(s.assessmentId().toString());
        assertThat(ids(review, "mappingIds")).containsExactlyInAnyOrder(s.foundMapping().toString(), s.missingMapping().toString());
        assertThat(count(signer, "REQUIREMENTS_TO_REVIEW", "UT")).as("UT is already decided").isZero();

        JsonNode request = item(contributor, "EVIDENCE_REQUESTS_OPEN", "CA");
        assertThat(ids(request, "mappingIds")).containsExactly(s.ambiguousMapping().toString());
        assertThat(request.get("ids").get("blockedCount").asLong()).isZero();

        JsonNode approve = item(reviewer, "RULE_VERSIONS_TO_APPROVE", "TX");
        assertThat(approve.get("programId").isNull()).isTrue();
        assertThat(approve.get("programName").isNull()).isTrue();
        assertThat(ids(approve, "ruleVersionIds")).containsExactly(approvable.toString());
        assertThat(ids(item(admin, "EXTRACTION_FAILED", "AZ"), "ruleVersionIds")).containsExactly(broken.toString());

        JsonNode changes = item(admin, "CHANGES_OPEN", null);
        assertThat(changes.get("ids").get("changeKind").asString()).isEqualTo("COURSE_VERSION");
        assertThat(ids(changes, "changeEventIds")).hasSize(1);
        assertThat(ids(changes, "programIds")).containsExactly(s.programId().toString());

        JsonNode ready = item(signer, "READY_TO_SIGN", "UT");
        assertThat(ready.get("count").asLong()).isEqualTo(1);
        assertThat(ready.get("ids").get("assessmentId").asString()).isEqualTo(s.assessmentId().toString());
        assertThat(item(signer, "READY_TO_SIGN", "CA")).as("CA still has undecided requirements").isNull();

        JsonNode failed = item(admin, "ASSESSMENTS_FAILED", null);
        assertThat(failed.get("programId").asString()).isEqualTo(failedProgram.toString());
        assertThat(ids(failed, "assessmentIds")).hasSize(1);
    }

    @Test
    void countsMoveWithEveryReviewAnswerChangeAndSignature() throws Exception {
        var s = california();
        UUID utMapping = utMapping(s);
        UUID schedule = fx.document(t, s.programId(), "ethics-schedule.txt", "HANDBOOK", null, ETHICS_SCHEDULE);

        assertThat(count(queue(t.reviewer()), "REQUIREMENTS_TO_REVIEW", "CA")).isEqualTo(3);
        assertThat(count(queue(t.reviewer()), "REQUIREMENTS_TO_REVIEW", "UT")).isEqualTo(1);
        assertThat(total(t.reviewer())).isEqualTo(4);
        assertThat(total(t.contributor())).isZero();
        assertThat(total(t.signer())).isEqualTo(4);

        review(s.foundMapping(), "ACCEPTED", null);
        assertThat(count(queue(t.reviewer()), "REQUIREMENTS_TO_REVIEW", "CA")).as("accepted").isEqualTo(2);

        review(s.ambiguousMapping(), "EVIDENCE_REQUESTED", "Please send the schedule.");
        JsonNode afterRequest = queue(t.reviewer());
        assertThat(count(afterRequest, "REQUIREMENTS_TO_REVIEW", "CA")).as("requested leaves review").isEqualTo(1);
        assertThat(count(afterRequest, "EVIDENCE_REQUESTS_OPEN", "CA")).as("and opens a request").isEqualTo(1);
        assertThat(total(t.contributor())).as("a contributor sees the request").isEqualTo(1);

        review(s.missingMapping(), "GAP_CONFIRMED", "No telehealth content anywhere in the curriculum.");
        JsonNode afterGap = queue(t.signer());
        assertThat(item(afterGap, "REQUIREMENTS_TO_REVIEW", "CA")).as("confirmed gap: CA fully decided").isNull();
        assertThat(count(afterGap, "READY_TO_SIGN", "CA")).as("the gate counts an open request as decided").isEqualTo(1);

        answer(s.ambiguousMapping(), schedule, "Week 2: Legal standards");
        JsonNode afterAnswer = queue(t.signer());
        assertThat(item(afterAnswer, "EVIDENCE_REQUESTS_OPEN", "CA")).as("answered leaves the request list").isNull();
        assertThat(count(afterAnswer, "REQUIREMENTS_TO_REVIEW", "CA")).as("and returns to review").isEqualTo(1);
        assertThat(item(afterAnswer, "READY_TO_SIGN", "CA")).as("so it is no longer ready").isNull();
        assertThat(total(t.contributor())).isZero();

        review(s.ambiguousMapping(), "ACCEPTED", "The schedule covers it.");
        review(utMapping, "ACCEPTED", null);
        JsonNode decided = queue(t.signer());
        assertThat(decided.get("items")).extracting(i -> i.get("kind").asString() + ":" + i.get("jurisdictionCode").asString())
                .containsExactly("READY_TO_SIGN:CA", "READY_TO_SIGN:UT");
        assertThat(total(t.reviewer())).isZero();
        assertThat(total(t.signer())).isEqualTo(2);

        // A new course version opens a change event and marks the mapping that cites the old syllabus stale.
        UUID newVersion = addCourseVersion(courseId(s.programId(), "COUN 605"), "2027-28");
        JsonNode afterChange = queue(t.signer());
        assertThat(count(afterChange, "CHANGES_OPEN", null)).as("one open change event").isEqualTo(1);
        assertThat(item(afterChange, "READY_TO_SIGN", "CA")).as("stale blocks signing").isNull();
        assertThat(count(afterChange, "READY_TO_SIGN", "UT")).isEqualTo(1);
        assertThat(count(queue(t.reviewer()), "REQUIREMENTS_TO_REVIEW", "CA")).as("stale cannot be reviewed").isZero();
        assertThat(total(t.admin())).isEqualTo(1);

        // Re-running against a syllabus that still contains the quote re-verifies it and closes the event.
        fx.document(t, s.programId(), "coun605-2027.txt", "SYLLABUS", newVersion, SYLLABUS_605 + "Revised for 2027-28.\n");
        UUID event = UUID.fromString(item(afterChange, "CHANGES_OPEN", null).get("ids").get("changeEventIds").get(0).asString());
        assertThat(mvc.post().uri("/api/v1/change-events/" + event + "/rerun").with(user(t.reviewer())).with(csrf()).exchange()).hasStatus(202);
        runner.runPending();
        JsonNode afterRerun = queue(t.signer());
        assertThat(item(afterRerun, "CHANGES_OPEN", null)).as("handled").isNull();
        assertThat(count(afterRerun, "READY_TO_SIGN", "CA")).as("evidence re-verified, decision kept").isEqualTo(1);

        assertThat(rf.sign(t.signer(), s.programId(), "CA", "MEETS")).hasStatus(201);
        JsonNode afterSigning = queue(t.signer());
        assertThat(item(afterSigning, "READY_TO_SIGN", "CA")).as("signed leaves the queue").isNull();
        assertThat(total(t.signer())).isEqualTo(1);
        assertThat(rf.sign(t.signer(), s.programId(), "UT", "MEETS")).hasStatus(201);
        assertThat(queue(t.signer()).get("items")).isEmpty();
        assertThat(queue(t.signer()).get("counts")).isEmpty();
        assertThat(total(t.signer())).isZero();
    }

    @Test
    void readyToSignAgreesWithTheSigningEndpointForEveryProgramAndJurisdiction() throws Exception {
        var a = california();
        rf.decideAll(t, a);                                      // CA ready (an open request counts as decided); UT undecided
        var b = anotherProgram(a);
        review(utMapping(b), "ACCEPTED", null);                  // UT ready; CA undecided
        var c = anotherProgram(a);
        rf.decideAll(t, c);
        assertThat(rf.sign(t.signer(), c.programId(), "CA", "MEETS")).hasStatus(201);   // CA already signed
        review(utMapping(c), "ACCEPTED", null);
        markStale(utMapping(c));                                 // UT decided but outdated
        UUID noAssessment = fx.program(t, List.of("CA", "UT"));  // nothing finished yet

        Map<String, UUID> programs = new LinkedHashMap<>();
        programs.put("a", a.programId());
        programs.put("b", b.programId());
        programs.put("c", c.programId());
        programs.put("none", noAssessment);

        Set<String> ready = new LinkedHashSet<>();
        for (JsonNode item : queue(t.signer()).get("items")) {
            if (item.get("kind").asString().equals("READY_TO_SIGN")) {
                ready.add(item.get("programId").asString() + ":" + item.get("jurisdictionCode").asString());
            }
        }
        assertThat(ready).containsExactlyInAnyOrder(a.programId() + ":CA", b.programId() + ":UT");

        for (var program : programs.entrySet()) {
            for (String jurisdiction : List.of("CA", "UT")) {
                boolean expected = ready.contains(program.getValue() + ":" + jurisdiction);
                var signed = rf.sign(t.signer(), program.getValue(), jurisdiction, "MEETS");
                assertThat(signed).as("sign %s %s", program.getKey(), jurisdiction).hasStatus(expected ? 201 : 409);
            }
        }
        assertThat(kinds(queue(t.signer()))).as("every ready item disappeared once signed").doesNotContain("READY_TO_SIGN");
    }

    @Test
    void otherTenantsItemsNeverAppear() throws Exception {
        california();
        var other = tenants.create("queue-other");
        for (LexvayUser as : List.of(other.contributor(), other.reviewer(), other.signer(), other.admin())) {
            var result = mvc.get().uri("/api/v1/work-queue").with(user(as)).exchange();
            assertThat(result).hasStatus(200);
            JsonNode body = fx.read(result);
            assertThat(body.get("items")).as("items of %s", as.role()).isEmpty();
            assertThat(body.get("counts")).isEmpty();
            var count = mvc.get().uri("/api/v1/work-queue/count").with(user(as)).exchange();
            assertThat(fx.read(count).get("total").asLong()).isZero();
        }
        assertThat(total(t.reviewer())).as("the first tenant still has its own").isEqualTo(4);
    }

    @Test
    void itemsOfAKindWaitOldestFirstAndListAtMostTwentyIds() throws Exception {
        createRuleVersion("Demo rule set Texas first", "TX", oneRequirement("TX"));
        UUID older = createRuleVersion("Demo rule set Arizona second", "AZ", oneRequirement("AZ"));
        for (int i = 0; i < 21; i++) {
            createRuleVersion("Demo rule set Arizona extra " + i, "AZ", oneRequirement("AZ"));
        }

        JsonNode queue = queue(t.reviewer());
        List<String> order = new ArrayList<>();
        queue.get("items").forEach(i -> order.add(i.get("jurisdictionCode").asString()));
        assertThat(order).as("Texas waited longest although Arizona sorts first").containsExactly("TX", "AZ");
        JsonNode arizona = item(queue, "RULE_VERSIONS_TO_APPROVE", "AZ");
        assertThat(arizona.get("count").asLong()).isEqualTo(22);
        assertThat(ids(arizona, "ruleVersionIds")).hasSize(20).startsWith(older.toString());
        assertThat(queue.get("counts").get("RULE_VERSIONS_TO_APPROVE").asLong()).isEqualTo(23);
        assertThat(total(t.reviewer())).isEqualTo(23);
    }

    @Test
    void staleEvidenceRequestsAreCountedAsBlockedNotAsOpen() throws Exception {
        var s = california();
        review(s.ambiguousMapping(), "EVIDENCE_REQUESTED", "Please send the schedule.");
        review(s.missingMapping(), "EVIDENCE_REQUESTED", "Is there any telehealth content?");
        markStale(s.missingMapping());

        JsonNode item = item(queue(t.contributor()), "EVIDENCE_REQUESTS_OPEN", "CA");
        assertThat(item.get("count").asLong()).isEqualTo(1);
        assertThat(ids(item, "mappingIds")).containsExactly(s.ambiguousMapping().toString());
        assertThat(item.get("ids").get("blockedCount").asLong()).isEqualTo(1);

        markStale(s.ambiguousMapping());
        assertThat(item(queue(t.contributor()), "EVIDENCE_REQUESTS_OPEN", "CA")).as("nothing answerable is not listed").isNull();
    }

    @Test
    void aSignedRequestIsNotAnswerableAndNoLongerListed() throws Exception {
        var s = california();
        rf.decideAll(t, s);                                      // CA: the ethics request stays open; every mapping decided
        assertThat(count(queue(t.contributor()), "EVIDENCE_REQUESTS_OPEN", "CA")).isEqualTo(1);
        assertThat(evidenceRequest(s).get("signed").asBoolean()).isFalse();

        assertThat(rf.sign(t.signer(), s.programId(), "CA", "DOES_NOT_MEET")).hasStatus(201);

        JsonNode view = evidenceRequest(s);
        assertThat(view.get("status").asString()).as("the status does not change").isEqualTo("OPEN");
        assertThat(view.get("signed").asBoolean()).isTrue();
        assertThat(item(queue(t.contributor()), "EVIDENCE_REQUESTS_OPEN", "CA")).as("nothing answerable is not listed").isNull();
        assertThat(total(t.contributor())).isZero();
        UUID schedule = fx.document(t, s.programId(), "ethics-schedule.txt", "HANDBOOK", null, ETHICS_SCHEDULE);
        var body = Map.of("documentId", schedule.toString(), "pageNumber", 1, "quote", "Week 2: Legal standards");
        assertThat(mvc.post().uri("/api/v1/mappings/" + s.ambiguousMapping() + "/evidence-responses").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange()).hasStatus(409);
    }

    @Test
    void aRequestOfAnotherJurisdictionStaysAnswerableWhenOnlyThisOneIsSigned() throws Exception {
        var s = california();
        rf.decideAll(t, s);
        review(utMapping(s), "EVIDENCE_REQUESTED", "Is the credit total documented?");
        assertThat(rf.sign(t.signer(), s.programId(), "CA", "MEETS")).hasStatus(201);

        JsonNode queue = queue(t.contributor());
        assertThat(item(queue, "EVIDENCE_REQUESTS_OPEN", "CA")).isNull();
        assertThat(ids(item(queue, "EVIDENCE_REQUESTS_OPEN", "UT"), "mappingIds")).containsExactly(utMapping(s).toString());
        assertThat(item(queue, "EVIDENCE_REQUESTS_OPEN", "UT").get("ids").get("blockedCount").asLong()).isZero();
    }

    @Test
    void aFailedAssessmentLeavesTheQueueOnceALaterRunFinishes() throws Exception {
        UUID programId = failedAssessmentProgram("CO");
        UUID second = fx.runAssessment(t, programId);
        assertThat(fx.assessment(t, second).get("status").asString()).isEqualTo("FAILED");
        JsonNode failures = item(queue(t.admin()), "ASSESSMENTS_FAILED", null);
        assertThat(failures.get("count").asLong()).as("no run finished yet: every failure is listed").isEqualTo(2);
        assertThat(ids(failures, "assessmentIds")).hasSize(2).contains(second.toString());

        fx.fakeScript("MATCH:CO-1", fx.matchOutput("EVIDENCE_MISSING", null, null, null, "Nothing mentions it."));
        UUID finished = fx.runAssessment(t, programId);
        assertThat(fx.assessment(t, finished).get("status").asString()).isEqualTo("FINISHED");
        assertThat(item(queue(t.admin()), "ASSESSMENTS_FAILED", null)).as("both earlier failures are followed by a success").isNull();
        assertThat(total(t.admin())).isZero();

        fake.reset();
        UUID third = fx.runAssessment(t, programId);
        assertThat(fx.assessment(t, third).get("status").asString()).isEqualTo("FAILED");
        JsonNode after = item(queue(t.admin()), "ASSESSMENTS_FAILED", null);
        assertThat(after.get("count").asLong()).isEqualTo(1);
        assertThat(ids(after, "assessmentIds")).containsExactly(third.toString());
        assertThat(total(t.admin())).isEqualTo(1);
    }

    @Test
    void approvingAndFailingMoveRuleVersionItems() throws Exception {
        UUID version = createRuleVersion("Demo rule set Texas to approve", "TX", oneRequirement("TX"));
        createRuleVersionWhoseExtractionFails("Demo rule set Arizona broken", "AZ");
        assertThat(count(queue(t.reviewer()), "RULE_VERSIONS_TO_APPROVE", "TX")).isEqualTo(1);
        assertThat(count(queue(t.admin()), "EXTRACTION_FAILED", "AZ")).isEqualTo(1);
        assertThat(item(queue(t.admin()), "RULE_VERSIONS_TO_APPROVE", "TX")).as("administrators do not approve").isNull();

        assertThat(mvc.post().uri("/api/v1/rule-versions/" + version + "/requirements/approve").with(user(t.reviewer())).with(csrf()).exchange())
                .hasStatus(200);
        assertThat(item(queue(t.reviewer()), "RULE_VERSIONS_TO_APPROVE", "TX")).isNull();
        assertThat(count(queue(t.reviewer()), "EXTRACTION_FAILED", "AZ")).as("failure stays until someone acts").isEqualTo(1);
    }

    @Test
    void anonymousCallersAreRefused() {
        assertThat(mvc.get().uri("/api/v1/work-queue").exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/work-queue/count").exchange()).hasStatus(401);
    }

    // --- fixtures that need a failing run ---

    private UUID createRuleVersionWhoseExtractionFails(String label, String jurisdiction) {
        String invalid = "{\"requirements\": \"nope\"}";
        UUID id = createRuleVersion(label, jurisdiction, invalid, invalid);
        var view = fx.read(mvc.get().uri("/api/v1/rule-versions/" + id).with(user(t.reviewer())).exchange());
        assertThat(view.get("extraction").get("status").asString()).isEqualTo("FAILED");
        return id;
    }

    /**
     * A program whose assessment fails: the rule version is approved and a document makes the requirement worth
     * asking the model about, but the model has no scripted answer.
     */
    private UUID failedAssessmentProgram(String jurisdiction) {
        UUID programId = fx.program(t, List.of(jurisdiction));
        fx.document(t, programId, "ethics.txt", "HANDBOOK", null, "Fictional. Prototype data. Coursework in professional ethics.");
        fx.approvedRuleVersion(t, jurisdiction, "Demo rule set " + jurisdiction + " unmatched", "Fictional rule.", oneRequirement(jurisdiction));
        UUID assessment = fx.runAssessment(t, programId);
        assertThat(fx.assessment(t, assessment).get("status").asString()).isEqualTo("FAILED");
        return programId;
    }
}
