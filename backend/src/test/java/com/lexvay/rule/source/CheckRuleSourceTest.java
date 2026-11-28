package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.document.TestDocuments;
import com.lexvay.job.JobStatus;
import com.lexvay.job.JobType;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The CHECK_RULE_SOURCE job (F12, D-184): what each outcome records, creates and leaves alone. */
class CheckRuleSourceTest extends AbstractRuleSourceTest {

    private static final String PAGE_TEXT = "Applicants need 66 semester hours.\nTelehealth training is required.";

    private UUID approvedCaliforniaRules() {
        return fx.approvedRuleVersion(t, "CA", "Demo rule set California 2026", "Fictional rule text for California.",
                fx.requirements("CA", List.of(AssessmentFixtures.subject("Ethics", "coursework in professional ethics"))));
    }

    @Test
    void aChangedPageCreatesAnUnapprovedDraftThatSupersedesTheApprovedVersion() {
        UUID approved = approvedCaliforniaRules();
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours.", "Telehealth training is required."));
        UUID sourceId = register("CA", url);
        long documentsBefore = count("document");

        assertThat(runner.runOne()).isTrue();

        var check = latestCheck(sourceId);
        assertThat(check).containsEntry("outcome", "CHANGED").containsEntry("http_status", 200);
        UUID draftId = (UUID) check.get("rule_version_id");
        UUID documentId = (UUID) check.get("document_id");

        var draft = ruleVersion(draftId);
        assertThat(draft.get("supersedes_id")).isEqualTo(approved);
        assertThat(draft.get("requirements_approved_at")).as("nothing approves a fetched version").isNull();
        assertThat(draft.get("source_url")).isEqualTo(url);
        assertThat(draft.get("label")).isEqualTo(fetchedLabel("CA"));
        assertThat(draft.get("effective_from")).as("a fetch date is not a legal date").isNull();
        assertThat(draft.get("legal_status")).isEqualTo("UNKNOWN");
        assertThat(draft.get("dates_basis")).isNull();
        assertThat(draft.get("observed_at")).as("observed when the check ran").isEqualTo(check.get("checked_at"));
        assertThat(draft.get("document_id")).isEqualTo(documentId);

        assertThat(count("document")).isEqualTo(documentsBefore + 1);
        var document = inTenant(() -> jdbc.sql("SELECT kind, filename, program_id, created_by FROM document WHERE id = :id")
                .param("id", documentId).query().singleRow());
        assertThat(document).containsEntry("kind", "RULE_TEXT").containsEntry("program_id", null).containsEntry("created_by", null);
        assertThat((String) document.get("filename")).endsWith(".html");
        assertThat(inTenant(() -> jdbc.sql("SELECT text FROM document_page WHERE document_id = :id ORDER BY page_number")
                .param("id", documentId).query(String.class).list())).containsExactly(PAGE_TEXT);

        var event = inTenant(() -> jdbc.sql("SELECT kind, status, old_id, created_by FROM change_event WHERE new_id = :id")
                .param("id", draftId).query().singleRow());
        assertThat(event).containsEntry("kind", "RULE_VERSION").containsEntry("status", "OPEN")
                .containsEntry("old_id", approved).containsEntry("created_by", null);
        var extraction = inTenant(() -> jobs.latest(JobType.EXTRACT_REQUIREMENTS, draftId)).orElseThrow();
        assertThat(extraction.status()).isEqualTo(JobStatus.QUEUED);

        var source = source(sourceId);
        assertThat((String) source.get("last_content_hash")).matches("[0-9a-f]{64}").isEqualTo(check.get("content_hash"));
        assertThat(source.get("last_checked_at")).isNotNull();
    }

    @Test
    void theCheckAndTheNewVersionAreAuditedAsTheSystem() {
        approvedCaliforniaRules();
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID sourceId = register("CA", url);
        runner.runOne();
        UUID draftId = (UUID) latestCheck(sourceId).get("rule_version_id");

        assertThat(auditActions(sourceId)).contains("RULE_SOURCE_CREATED", "RULE_SOURCE_CHECKED");
        var checked = inTenant(() -> audit.forEntity(sourceId)).stream().filter(e -> e.action().equals("RULE_SOURCE_CHECKED")).findFirst().orElseThrow();
        assertThat(checked.userId()).as("done by the system, not by a person").isNull();
        assertThat(checked.detail()).containsEntry("outcome", "CHANGED").containsEntry("ruleVersionId", draftId.toString())
                .containsKeys("jobId", "checkId", "documentId");
        assertThat(auditActions(draftId)).contains("RULE_VERSION_CREATED");
        assertThat(inTenant(() -> audit.forEntity(draftId)).stream().filter(e -> e.action().equals("RULE_VERSION_CREATED")).findFirst().orElseThrow().userId()).isNull();
    }

    @Test
    void extractionFollowsTheDraftButNothingApprovesIt() {
        approvedCaliforniaRules();
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID sourceId = register("CA", url);
        runner.runOne();
        UUID draftId = (UUID) latestCheck(sourceId).get("rule_version_id");

        scriptExtraction(fetchedLabel("CA"));
        runner.runPending();

        assertThat(inTenant(() -> jdbc.sql("SELECT count(*) FROM requirement WHERE rule_version_id = :id").param("id", draftId).query(Long.class).single()))
                .isEqualTo(1);
        assertThat(ruleVersion(draftId).get("requirements_approved_at")).isNull();
        assertThat(body(mvc.get().uri("/api/v1/rule-versions/" + draftId).with(user(t.reviewer())).exchange()).get("approval").isNull())
                .isTrue();
    }

    @Test
    void theFirstCheckIsChangedEvenWhenNoVersionExistsYet() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID sourceId = register("TX", url);

        runner.runOne();

        var check = latestCheck(sourceId);
        assertThat(check).containsEntry("outcome", "CHANGED");
        UUID draftId = (UUID) check.get("rule_version_id");
        assertThat(ruleVersion(draftId).get("supersedes_id")).isNull();
        assertThat(ruleVersion(draftId).get("requirements_approved_at")).isNull();
        assertThat(inTenant(() -> jdbc.sql("SELECT count(*) FROM change_event WHERE new_id = :id").param("id", draftId).query(Long.class).single()))
                .as("there is no earlier version to change from").isZero();
    }

    @Test
    void anUnchangedPageCreatesNothingAndMovesTheCheckTime() throws InterruptedException {
        approvedCaliforniaRules();
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID sourceId = register("CA", url);
        runner.runOne();
        var before = source(sourceId);
        long documents = count("document");
        long versions = count("rule_version");
        long changes = count("change_event");
        Thread.sleep(20);

        checkNow(sourceId);

        assertThat(latestCheck(sourceId)).containsEntry("outcome", "UNCHANGED").containsEntry("http_status", 200)
                .containsEntry("document_id", null).containsEntry("rule_version_id", null);
        assertThat(count("document")).isEqualTo(documents);
        assertThat(count("rule_version")).isEqualTo(versions);
        assertThat(count("change_event")).isEqualTo(changes);
        var after = source(sourceId);
        assertThat(((Timestamp) after.get("last_checked_at")).toInstant()).isAfter(((Timestamp) before.get("last_checked_at")).toInstant());
        assertThat(after.get("last_content_hash")).isEqualTo(before.get("last_content_hash"));
        assertThat(checkCount(sourceId)).isEqualTo(2);
    }

    @Test
    void whitespaceAndMarkupOnlyChangesAreUnchangedButANewWordIsNot() {
        approvedCaliforniaRules();
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours.", "Telehealth training is required."));
        UUID sourceId = register("CA", url);
        runner.runOne();
        UUID draftId = (UUID) latestCheck(sourceId).get("rule_version_id");
        long versions = count("rule_version");

        fetcher.html(url, "<html lang=\"en\"><head><title>New title</title><script>track()</script></head><body class=\"x\">"
                + "<header>Other header</header><div id=\"main\"><span>Applicants   need 66</span>\n   semester hours.</div>\n\n"
                + "<section><p>Telehealth   <em>training</em> is\n required.</p></section><!-- edited --></body></html>");
        checkNow(sourceId);
        assertThat(latestCheck(sourceId)).as("same words, other markup").containsEntry("outcome", "UNCHANGED");

        String baseline = (String) source(sourceId).get("last_content_hash");
        fetcher.html(url, page("Applicants need 72 semester hours.", "Telehealth training is required."));
        checkNow(sourceId);

        var check = latestCheck(sourceId);
        assertThat(check).containsEntry("outcome", "CHANGED_QUEUED").containsEntry("rule_version_id", draftId);
        assertThat(check.get("document_id")).as("the changed text is stored").isNotNull();
        assertThat((String) check.get("detail")).contains(draftId.toString());
        assertThat(count("rule_version")).as("no second draft").isEqualTo(versions);
        assertThat(source(sourceId).get("last_content_hash")).as("the baseline is the drafted text").isEqualTo(baseline);
    }

    @Test
    void aChangeSeenWhileADraftWaitedIsPickedUpOnceTheDraftIsApproved() {
        approvedCaliforniaRules();
        scriptExtraction(fetchedLabel("CA"));
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID sourceId = register("CA", url);
        runner.runOne();
        UUID first = (UUID) latestCheck(sourceId).get("rule_version_id");
        fetcher.html(url, page("Applicants need 72 semester hours."));
        checkNow(sourceId);
        assertThat(latestCheck(sourceId)).containsEntry("outcome", "CHANGED_QUEUED");

        extractAndApprove(first, fetchedLabel("CA"), LocalDate.of(2026, 7, 1));
        checkNow(sourceId);

        var check = latestCheck(sourceId);
        assertThat(check).containsEntry("outcome", "CHANGED");
        UUID second = (UUID) check.get("rule_version_id");
        assertThat(ruleVersion(second).get("supersedes_id")).isEqualTo(first);
        assertThat(ruleVersion(second).get("requirements_approved_at")).isNull();
    }

    @Test
    void everyChangeSeenWhileADraftWaitsIsKeptInOrderBehindIt() {
        approvedCaliforniaRules();
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID sourceId = register("CA", url);
        runner.runOne();
        UUID draftId = (UUID) latestCheck(sourceId).get("rule_version_id");
        String baseline = (String) source(sourceId).get("last_content_hash");
        long versions = count("rule_version");
        List<String> observed = List.of("Applicants need 72 semester hours.", "Applicants need 75 semester hours.",
                "Applicants need 80 semester hours.");

        for (String text : observed) {
            fetcher.html(url, page(text));
            checkNow(sourceId);
            assertThat(latestCheck(sourceId)).containsEntry("outcome", "CHANGED_QUEUED").containsEntry("rule_version_id", draftId);
        }
        checkNow(sourceId);

        assertThat(latestCheck(sourceId)).as("the same text twice is kept once").containsEntry("outcome", "UNCHANGED");
        var kept = inTenant(() -> jdbc.sql("SELECT c.id, c.document_id, p.text FROM rule_source_check c "
                + "JOIN document_page p ON p.document_id = c.document_id WHERE c.rule_source_id = :id AND c.outcome = 'CHANGED_QUEUED' "
                + "ORDER BY c.checked_at, c.id").param("id", sourceId).query().listOfRows());
        assertThat(kept).extracting(r -> r.get("text")).containsExactlyElementsOf(observed);
        assertThat(count("rule_version")).as("one draft, not three").isEqualTo(versions);
        assertThat(source(sourceId).get("last_content_hash")).as("the baseline stays the drafted text").isEqualTo(baseline);

        var view = body(mvc.get().uri("/api/v1/rule-versions/" + draftId).with(user(t.reviewer())).exchange());
        var queued = view.get("queuedObservations");
        assertThat(queued).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(queued.get(i).get("checkId").asString()).isEqualTo(kept.get(i).get("id").toString());
            assertThat(queued.get(i).get("documentId").asString()).isEqualTo(kept.get(i).get("document_id").toString());
            assertThat(queued.get(i).get("contentHash").asString()).matches("[0-9a-f]{64}");
            if (i > 0) {
                assertThat(queued.get(i).get("observedAt").asString()).isGreaterThan(queued.get(i - 1).get("observedAt").asString());
            }
        }
        assertThat(view.get("effectiveFrom").isNull()).isTrue();
        assertThat(view.get("legalStatus").asString()).isEqualTo("UNKNOWN");
        assertThat(view.get("observedAt").isNull()).isFalse();
        var listed = body(mvc.get().uri("/api/v1/rule-versions").with(user(t.reviewer())).exchange());
        for (JsonNode listedVersion : listed) {
            boolean isDraft = listedVersion.get("id").asString().equals(draftId.toString());
            assertThat(listedVersion.get("queuedObservations")).hasSize(isDraft ? 3 : 0);
        }
    }

    @Test
    void textThatReturnsToAnEarlierPageReusesTheStoredDocument() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID sourceId = register("TX", url);
        runner.runOne();
        UUID first = (UUID) latestCheck(sourceId).get("rule_version_id");
        UUID firstDocument = (UUID) latestCheck(sourceId).get("document_id");
        extractAndApprove(first, fetchedLabel("TX"), LocalDate.of(2026, 7, 1));

        fetcher.html(url, page("Applicants need 72 semester hours."));
        checkNow(sourceId);
        UUID second = (UUID) latestCheck(sourceId).get("rule_version_id");
        assertThat(latestCheck(sourceId).get("document_id")).isNotEqualTo(firstDocument);
        extractAndApprove(second, fetchedLabel("TX"), LocalDate.of(2026, 8, 1));
        long documents = count("document");

        fetcher.html(url, page("Applicants need 66 semester hours."));
        checkNow(sourceId);

        var check = latestCheck(sourceId);
        assertThat(check).containsEntry("outcome", "CHANGED").containsEntry("document_id", firstDocument);
        assertThat(ruleVersion(check.get("rule_version_id")).get("supersedes_id")).isEqualTo(second);
        assertThat(count("document")).isEqualTo(documents);
    }

    @Test
    void aPdfIsReadThroughTheSameExtractorAsAnUpload() {
        String url = newUrl("pdf");
        fetcher.respond(url, 200, "application/pdf",
                TestDocuments.pdf(List.of(List.of("Applicants need 66 semester hours."), List.of("Second page of rules."))));
        UUID sourceId = register("CA", url);

        runner.runOne();

        var check = latestCheck(sourceId);
        assertThat(check).containsEntry("outcome", "CHANGED");
        UUID documentId = (UUID) check.get("document_id");
        assertThat(inTenant(() -> jdbc.sql("SELECT filename FROM document WHERE id = :id").param("id", documentId).query(String.class).single()))
                .endsWith(".pdf");
        assertThat(inTenant(() -> jdbc.sql("SELECT text FROM document_page WHERE document_id = :id ORDER BY page_number")
                .param("id", documentId).query(String.class).list()))
                .hasSize(2).first().asString().contains("66 semester hours");
    }

    private record Failure(String name, Consumer<String> script, Integer status, String detail) {
    }

    @Test
    void aPageThatCannotBeFetchedOrReadIsARecordedCheckAndTheJobEndsDone() {
        List<Failure> failures = List.of(
                new Failure("server error", url -> fetcher.respond(url, 500, "text/plain", "oops".getBytes(StandardCharsets.UTF_8)), 500,
                        "The server answered HTTP 500"),
                new Failure("not found", url -> fetcher.respond(url, 404, "text/html", "<p>gone</p>".getBytes(StandardCharsets.UTF_8)), 404,
                        "The server answered HTTP 404"),
                new Failure("body over the cap", url -> fetcher.reject(url, "Could not fetch the address: the response is larger than 20 MB"), null,
                        "larger than 20 MB"),
                new Failure("address not allowed", url -> fetcher.reject(url, "The host internal.example.test resolves to an internal address, which is not fetched"),
                        null, "internal address"),
                new Failure("not a document", url -> fetcher.respond(url, 200, "image/png", new byte[] {1, 2, 3}), 200,
                        "Unsupported content type image/png"),
                new Failure("no text", url -> fetcher.html(url, "<html><body><nav>Menu</nav></body></html>"), 200, "no extractable text"),
                new Failure("corrupt pdf", url -> fetcher.respond(url, 200, "application/pdf", "%PDF-1.4 not really".getBytes(StandardCharsets.UTF_8)), 200,
                        "could not be parsed as a PDF"));
        long documents = count("document");
        long versions = count("rule_version");

        for (Failure failure : failures) {
            String url = newUrl("html");
            failure.script().accept(url);
            var created = post("/api/v1/rule-sources", sourceBody("CA", url));
            assertThat(created).hasStatus(201);
            UUID sourceId = UUID.fromString(body(created).get("id").asString());
            UUID jobId = UUID.fromString(body(created).get("jobId").asString());

            runner.runOne();

            var check = latestCheck(sourceId);
            assertThat(check).as(failure.name()).containsEntry("outcome", "FETCH_FAILED").containsEntry("http_status", failure.status())
                    .containsEntry("document_id", null).containsEntry("rule_version_id", null).containsEntry("content_hash", null);
            assertThat((String) check.get("detail")).as(failure.name()).contains(failure.detail());
            assertThat(inTenant(() -> jobs.findById(jobId)).orElseThrow().status()).as(failure.name()).isEqualTo(JobStatus.DONE);
            var state = source(sourceId);
            assertThat(state.get("last_checked_at")).as("the schedule retries after the interval").isNotNull();
            assertThat(state.get("last_content_hash")).isNull();
        }
        assertThat(count("document")).isEqualTo(documents);
        assertThat(count("rule_version")).isEqualTo(versions);
    }

    @Test
    void aFailureNeverMovesTheBaseline() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID sourceId = register("TX", url);
        runner.runOne();
        String baseline = (String) source(sourceId).get("last_content_hash");
        long versions = count("rule_version");

        fetcher.respond(url, 503, "text/plain", new byte[0]);
        checkNow(sourceId);
        assertThat(latestCheck(sourceId)).containsEntry("outcome", "FETCH_FAILED").containsEntry("http_status", 503);
        assertThat(source(sourceId).get("last_content_hash")).isEqualTo(baseline);

        fetcher.html(url, page("Applicants need 66 semester hours."));
        checkNow(sourceId);

        assertThat(latestCheck(sourceId)).containsEntry("outcome", "UNCHANGED");
        assertThat(count("rule_version")).isEqualTo(versions);
    }

    @Test
    void theChecksOfTwoSourcesStayApart() {
        Map<String, UUID> sources = new LinkedHashMap<>();
        for (String jurisdiction : List.of("CA", "TX")) {
            String url = newUrl("html");
            fetcher.html(url, page("Rules of " + jurisdiction + " " + UUID.randomUUID()));
            sources.put(jurisdiction, register(jurisdiction, url));
        }

        runner.runOne();
        runner.runOne();

        var ca = latestCheck(sources.get("CA"));
        var tx = latestCheck(sources.get("TX"));
        assertThat(ca).containsEntry("outcome", "CHANGED");
        assertThat(tx).containsEntry("outcome", "CHANGED");
        assertThat(ca.get("rule_version_id")).isNotEqualTo(tx.get("rule_version_id"));
    }
}
