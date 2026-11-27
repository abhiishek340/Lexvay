package com.lexvay.review;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.audit.AuditRepository;
import com.lexvay.auth.LexvayUser;
import com.lexvay.common.TenantContext;
import com.lexvay.document.DocumentPage;
import com.lexvay.document.DocumentRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

class EvidenceRequestApiTest extends AbstractAssessmentTest {

    static final String ETHICS_SCHEDULE = """
            Fictional. Prototype data. COUN 605 weekly schedule.
            Week 1: The ACA Code
            of Ethics and professional identity.
            Week 2: Legal standards, informed consent and confidentiality.
            """;

    @Autowired AuditRepository audit;
    @Autowired DocumentRepository documents;
    @Autowired JdbcClient jdbc;

    ReviewFixtures rf;
    ReviewFixtures.Scenario s;
    UUID schedule;

    @BeforeEach
    void scenario() {
        rf = new ReviewFixtures(mvc, json, fx);
        s = rf.californiaScenario(t, "evidence " + UUID.randomUUID().toString().substring(0, 6));
        schedule = fx.document(t, s.programId(), "ethics-schedule.txt", "HANDBOOK", null, ETHICS_SCHEDULE);
    }

    private MvcTestResult respond(LexvayUser as, UUID mappingId, UUID documentId, int page, String quote, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("documentId", documentId.toString());
        body.put("pageNumber", page);
        body.put("quote", quote);
        body.put("comment", comment);
        return mvc.post().uri("/api/v1/mappings/" + mappingId + "/evidence-responses").with(user(as)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
    }

    private JsonNode requests(LexvayUser as, UUID programId, String status) {
        String query = status == null ? "" : "?status=" + status;
        var result = mvc.get().uri("/api/v1/programs/" + programId + "/evidence-requests" + query).with(user(as)).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result);
    }

    private JsonNode responses(LexvayUser as, UUID mappingId) {
        var result = mvc.get().uri("/api/v1/mappings/" + mappingId + "/evidence-responses").with(user(as)).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result);
    }

    private JsonNode mapping(UUID mappingId, String ref) {
        return fx.mappingFor(fx.mappings(t, s.assessmentId(), "CA"), ref);
    }

    private void request(UUID mappingId, String note) {
        assertThat(rf.review(t.reviewer(), mappingId, "EVIDENCE_REQUESTED", note)).hasStatus(201);
    }

    private long count(String table) {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single());
    }

    @Test
    void fullLoopFromRequestToReviewerDecision() throws Exception {
        assertThat(requests(t.contributor(), s.programId(), null)).isEmpty();
        request(s.ambiguousMapping(), "Please send the week-by-week ethics schedule.");

        JsonNode open = requests(t.contributor(), s.programId(), null);
        assertThat(open).hasSize(1);
        JsonNode row = open.get(0);
        assertThat(row.get("mappingId").asString()).isEqualTo(s.ambiguousMapping().toString());
        assertThat(row.get("status").asString()).isEqualTo("OPEN");
        assertThat(row.get("jurisdiction").asString()).isEqualTo("CA");
        assertThat(row.get("requirementRef").asString()).isEqualTo("CA-3");
        assertThat(row.get("requirementTitle").asString()).isEqualTo("Ethics");
        assertThat(row.get("requestNote").asString()).isEqualTo("Please send the week-by-week ethics schedule.");
        assertThat(row.get("requestedBy").asString()).isEqualTo(t.reviewer().id().toString());
        assertThat(row.get("requestedByName").asString()).isEqualTo(t.reviewer().displayName());
        assertThat(row.get("requestedAt").asString()).isNotBlank();
        assertThat(row.get("responseCount").asInt()).isZero();
        assertThat(row.get("latestResponse").isNull()).isTrue();
        assertThat(requests(t.contributor(), s.programId(), "ANSWERED")).isEmpty();
        assertThat(responses(t.contributor(), s.ambiguousMapping())).isEmpty();

        JsonNode proposedBefore = mapping(s.ambiguousMapping(), "CA-3");
        long spansBefore = count("evidence_span");

        var created = respond(t.contributor(), s.ambiguousMapping(), schedule, 1,
                "Week 1: The ACA Code of   Ethics and professional identity.", "  Attached the schedule.  ");
        assertThat(created).hasStatus(201);
        JsonNode response = fx.read(created);
        assertThat(count("evidence_span")).isEqualTo(spansBefore + 1);

        DocumentPage page = TenantContext.runAs(t.tenantId(), () -> documents.page(schedule, 1).orElseThrow());
        int start = response.get("charStart").asInt();
        int end = response.get("charEnd").asInt();
        assertThat(page.text().substring(start, end)).isEqualTo(response.get("quote").asString());
        assertThat(response.get("quote").asString()).isEqualTo("Week 1: The ACA Code\nof Ethics and professional identity.");
        assertThat(start).isEqualTo(page.text().indexOf("Week 1"));
        assertThat(response.get("documentId").asString()).isEqualTo(schedule.toString());
        assertThat(response.get("filename").asString()).isEqualTo("ethics-schedule.txt");
        assertThat(response.get("kind").asString()).isEqualTo("HANDBOOK");
        assertThat(response.get("courseCode").isNull()).isTrue();
        assertThat(response.get("pageNumber").asInt()).isEqualTo(1);
        assertThat(response.get("comment").asString()).isEqualTo("Attached the schedule.");
        assertThat(response.get("providedBy").asString()).isEqualTo(t.contributor().id().toString());
        assertThat(response.get("providedByName").asString()).isEqualTo(t.contributor().displayName());

        JsonNode proposedAfter = mapping(s.ambiguousMapping(), "CA-3");
        assertThat(proposedAfter.get("decision").isNull()).as("the requirement returns to needs review").isTrue();
        for (String field : List.of("finding", "method", "rationale", "evidence", "stale")) {
            assertThat(proposedAfter.get(field)).as(field + " is never changed by a response").isEqualTo(proposedBefore.get(field));
        }

        assertThat(requests(t.reviewer(), s.programId(), null)).as("default is OPEN").isEmpty();
        JsonNode answered = requests(t.reviewer(), s.programId(), "ANSWERED");
        assertThat(answered).hasSize(1);
        assertThat(answered.get(0).get("status").asString()).isEqualTo("ANSWERED");
        assertThat(answered.get(0).get("requestNote").asString()).isEqualTo("Please send the week-by-week ethics schedule.");
        assertThat(answered.get(0).get("responseCount").asInt()).isEqualTo(1);
        JsonNode latest = answered.get(0).get("latestResponse");
        assertThat(latest.get("id").asString()).isEqualTo(response.get("id").asString());
        assertThat(latest.get("providedByName").asString()).isEqualTo(t.contributor().displayName());
        assertThat(latest.get("filename").asString()).isEqualTo("ethics-schedule.txt");
        assertThat(latest.get("pageNumber").asInt()).isEqualTo(1);
        assertThat(requests(t.reviewer(), s.programId(), "ALL")).hasSize(1);

        JsonNode listed = responses(t.reviewer(), s.ambiguousMapping());
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0)).isEqualTo(response);

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(s.ambiguousMapping()));
        assertThat(entries).extracting(e -> e.action()).contains("EVIDENCE_PROVIDED");
        var provided = entries.stream().filter(e -> e.action().equals("EVIDENCE_PROVIDED")).findFirst().orElseThrow();
        assertThat(provided.userId()).isEqualTo(t.contributor().id());
        assertThat(provided.detail()).containsEntry("requirementRef", "CA-3").containsEntry("jurisdiction", "CA")
                .containsEntry("documentId", schedule.toString()).containsEntry("quote", response.get("quote").asString())
                .containsEntry("evidenceResponseId", response.get("id").asString());
        assertThat(((Number) provided.detail().get("pageNumber")).intValue()).isEqualTo(1);

        var note = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT kind, note, user_id FROM review_event WHERE mapping_id = :m ORDER BY created_at DESC, id DESC LIMIT 1")
                .param("m", s.ambiguousMapping()).query().singleRow());
        assertThat(note.get("kind")).isEqualTo("REOPENED");
        assertThat(note.get("note")).isEqualTo("Evidence provided by " + t.contributor().displayName());
        assertThat(note.get("user_id")).isEqualTo(t.contributor().id());

        assertThat(rf.review(t.reviewer(), s.ambiguousMapping(), "ACCEPTED", "Schedule covers the ethics code.")).hasStatus(201);
        assertThat(requests(t.reviewer(), s.programId(), "ALL")).as("decided: no longer listed").isEmpty();
        assertThat(responses(t.reviewer(), s.ambiguousMapping())).as("the response stays on record").hasSize(1);
    }

    @Test
    void aNewRequestAfterAnAnswerIsOpenAgainAndShowsThePreviousAnswer() throws Exception {
        request(s.ambiguousMapping(), "First ask.");
        assertThat(respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "Week 2: Legal standards", null)).hasStatus(201);
        assertThat(responses(t.contributor(), s.ambiguousMapping()).get(0).get("comment").isNull()).isTrue();
        request(s.ambiguousMapping(), "Second ask: which week covers duty to warn?");

        JsonNode open = requests(t.contributor(), s.programId(), "OPEN");
        assertThat(open).hasSize(1);
        assertThat(open.get(0).get("requestNote").asString()).startsWith("Second ask");
        assertThat(open.get(0).get("responseCount").asInt()).isEqualTo(1);
        assertThat(open.get(0).get("latestResponse").get("filename").asString()).isEqualTo("ethics-schedule.txt");

        assertThat(respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "informed consent and confidentiality", "Week 2.")).hasStatus(201);
        JsonNode listed = responses(t.contributor(), s.ambiguousMapping());
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).get("comment").asString()).isEqualTo("Week 2.");
        assertThat(requests(t.contributor(), s.programId(), "ANSWERED").get(0).get("responseCount").asInt()).isEqualTo(2);
    }

    @Test
    void aSystemReopeningAfterTheAnswerIsNeitherOpenNorAnswered() throws Exception {
        request(s.ambiguousMapping(), "Please send it.");
        assertThat(respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "Week 2", null)).hasStatus(201);
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                        "INSERT INTO review_event (tenant_id, mapping_id, kind, note, user_id, created_at) VALUES (:t, :m, 'REOPENED', 'rule changed', NULL, clock_timestamp())")
                .param("t", t.tenantId()).param("m", s.ambiguousMapping()).update());
        assertThat(requests(t.reviewer(), s.programId(), "ALL")).isEmpty();
    }

    @Test
    void fabricatedQuoteIsRejectedAndNothingIsStored() throws Exception {
        request(s.ambiguousMapping(), "Please send it.");
        long spans = count("evidence_span");
        long events = count("review_event");
        long audits = count("audit_log");

        var fabricated = respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "Week 1: Telehealth ethics and supervision.", "x");
        assertThat(fabricated).hasStatus(400);
        assertThat(fx.read(fabricated).get("detail").asString()).contains("not found on page 1").contains("ethics-schedule.txt");

        assertThat(respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "   ", null)).hasStatus(400);
        assertThat(respond(t.contributor(), s.ambiguousMapping(), schedule, 0, "Week 2", null)).hasStatus(400);
        assertThat(respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "Week 2", "c".repeat(4001))).hasStatus(400);

        assertThat(count("evidence_span")).isEqualTo(spans);
        assertThat(count("review_event")).isEqualTo(events);
        assertThat(count("audit_log")).isEqualTo(audits);
        assertThat(count("evidence_response")).isZero();
        assertThat(requests(t.contributor(), s.programId(), "OPEN")).hasSize(1);
    }

    @Test
    void documentMustBeAProgramDocumentWithThatPage() throws Exception {
        request(s.ambiguousMapping(), "Please send it.");
        UUID otherProgram = fx.program(t, List.of("CA"));
        UUID foreign = fx.document(t, otherProgram, "other-program.txt", "HANDBOOK", null, ETHICS_SCHEDULE);
        var wrongProgram = respond(t.contributor(), s.ambiguousMapping(), foreign, 1, "Week 2", null);
        assertThat(wrongProgram).hasStatus(400);
        assertThat(fx.read(wrongProgram).get("detail").asString()).contains("different program");

        UUID ruleDocument = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT document_id FROM rule_version WHERE id = :id").param("id", s.ruleVersionId()).query(UUID.class).single());
        var ruleText = respond(t.contributor(), s.ambiguousMapping(), ruleDocument, 1, "Fictional", null);
        assertThat(ruleText).hasStatus(400);
        assertThat(fx.read(ruleText).get("detail").asString()).contains("Rule text");

        var noPage = respond(t.contributor(), s.ambiguousMapping(), schedule, 7, "Week 2", null);
        assertThat(noPage).hasStatus(400);
        assertThat(fx.read(noPage).get("detail").asString()).contains("Page 7 does not exist");

        assertThat(respond(t.contributor(), s.ambiguousMapping(), UUID.randomUUID(), 1, "Week 2", null)).hasStatus(400);
        assertThat(count("evidence_response")).isZero();
    }

    @Test
    void thereMustBeAnOpenRequest() throws Exception {
        var none = respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "Week 2", null);
        assertThat(none).hasStatus(409);
        assertThat(fx.read(none).get("detail").asString()).isEqualTo("No open evidence request for this requirement");

        assertThat(rf.review(t.reviewer(), s.foundMapping(), "ACCEPTED", null)).hasStatus(201);
        assertThat(respond(t.contributor(), s.foundMapping(), schedule, 1, "Week 2", null)).as("decided, not requested").hasStatus(409);

        request(s.ambiguousMapping(), "Please send it.");
        assertThat(respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "Week 2", null)).hasStatus(201);
        assertThat(respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "Week 2", null)).as("already answered").hasStatus(409);
        assertThat(count("evidence_response")).isEqualTo(1);
    }

    @Test
    void staleAndSignedMappingsAreRefused() throws Exception {
        rf.decideAll(t, s);
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("UPDATE mapping SET stale = true WHERE id = :id")
                .param("id", s.ambiguousMapping()).update());
        var stale = respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "Week 2", null);
        assertThat(stale).hasStatus(409);
        assertThat(fx.read(stale).get("detail").asString()).contains("outdated");
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("UPDATE mapping SET stale = false WHERE id = :id")
                .param("id", s.ambiguousMapping()).update());

        assertThat(requests(t.reviewer(), s.programId(), "OPEN").get(0).get("signed").asBoolean()).as("not signed yet").isFalse();
        assertThat(rf.sign(t.signer(), s.programId(), "CA", "DOES_NOT_MEET")).hasStatus(201);
        JsonNode listed = requests(t.reviewer(), s.programId(), "OPEN");
        assertThat(listed).as("the status stays OPEN").hasSize(1);
        assertThat(listed.get(0).get("signed").asBoolean()).isTrue();
        var signed = respond(t.contributor(), s.ambiguousMapping(), schedule, 1, "Week 2", null);
        assertThat(signed).hasStatus(409);
        assertThat(fx.read(signed).get("detail").asString()).contains("signed determination");
        assertThat(count("evidence_response")).isZero();
    }

    @Test
    void everyRoleMayAnswerAndAnonymousCallersMayNot() throws Exception {
        for (LexvayUser who : List.of(t.contributor(), t.reviewer(), t.signer(), t.admin())) {
            request(s.ambiguousMapping(), "Ask for " + who.role());
            var answered = respond(who, s.ambiguousMapping(), schedule, 1, "Week 2", null);
            assertThat(answered).as(who.role().name()).hasStatus(201);
            assertThat(fx.read(answered).get("providedBy").asString()).isEqualTo(who.id().toString());
        }
        assertThat(responses(t.admin(), s.ambiguousMapping())).hasSize(4);

        request(s.ambiguousMapping(), "One more.");
        var body = json.writeValueAsString(Map.of("documentId", schedule.toString(), "pageNumber", 1, "quote", "Week 2"));
        assertThat(mvc.post().uri("/api/v1/mappings/" + s.ambiguousMapping() + "/evidence-responses").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/mappings/" + s.ambiguousMapping() + "/evidence-responses").exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/programs/" + s.programId() + "/evidence-requests").exchange()).hasStatus(401);
    }

    @Test
    void statusFilterIsValidatedAndOtherTenantsSeeNothing() throws Exception {
        request(s.ambiguousMapping(), "Please send it.");
        assertThat(mvc.get().uri("/api/v1/programs/" + s.programId() + "/evidence-requests?status=DONE")
                .with(user(t.reviewer())).exchange()).hasStatus(400);

        var other = tenants.create("other");
        assertThat(respond(other.admin(), s.ambiguousMapping(), schedule, 1, "Week 2", null)).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/mappings/" + s.ambiguousMapping() + "/evidence-responses").with(user(other.admin()))
                .exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/programs/" + s.programId() + "/evidence-requests").with(user(other.admin()))
                .exchange()).hasStatus(404);
        assertThat(respond(t.contributor(), UUID.randomUUID(), schedule, 1, "Week 2", null)).hasStatus(404);
        assertThat(count("evidence_response")).isZero();
    }

    @Test
    void aProgramWithoutAFinishedAssessmentHasNoRequests() throws Exception {
        UUID fresh = fx.program(t, List.of("CA"));
        assertThat(requests(t.reviewer(), fresh, "ALL")).isEmpty();
    }
}
