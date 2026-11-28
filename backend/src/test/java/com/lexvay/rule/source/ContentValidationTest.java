package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Content is validated before a change candidate exists (RG-2, D-572): a text without the identifiers the source is
 * known to contain, a challenge or block page, and a format the address does not promise are SUSPECT_CONTENT. A suspect
 * text makes no draft and does not move the baseline.
 */
class ContentValidationTest extends AbstractRuleSourceTest {

    private static final String RULE_TEXT = "Applicants need 66 semester hours under R156-60e. Telehealth training is required.";

    private long ruleVersions() {
        return count("rule_version");
    }

    @Test
    void aTextWithoutAnExpectedIdentifierIsSuspectAndMakesNoDraft() {
        String url = newUrl("html");
        UUID id = register("CA", url);
        govern(id, body -> body.put("expectedIdentifiers", List.of("R156-60e", "Section 12")));
        fetcher.html(url, page("Applicants need 66 semester hours under R156-60e."));
        long versionsBefore = ruleVersions();

        runner.runOne();

        var check = latestCheck(id);
        assertThat(check).containsEntry("outcome", "SUSPECT_CONTENT").containsEntry("http_status", 200);
        assertThat((String) check.get("detail")).contains("does not contain").contains("\"Section 12\"").doesNotContain("R156-60e");
        assertThat(check.get("content_hash")).isNull();
        assertThat(check.get("document_id")).isNull();
        assertThat(ruleVersions()).as("no change candidate").isEqualTo(versionsBefore);
        assertThat(source(id)).as("the baseline stays empty").containsEntry("last_content_hash", null);
        assertThat(auditActions(id)).contains("RULE_SOURCE_CHECKED");
    }

    @Test
    void identifiersAreFoundWhateverTheCaseAndWhiteSpace() {
        String url = newUrl("html");
        UUID id = register("CA", url);
        govern(id, body -> body.put("expectedIdentifiers", List.of("r156-60E", "semester   hours")));
        fetcher.html(url, page("Applicants need 66 semester\n hours under R156-60e."));

        runner.runOne();

        assertThat(latestCheck(id)).containsEntry("outcome", "CHANGED");
    }

    @Test
    void aSuspectTextNeverMovesTheBaseline() {
        String url = newUrl("html");
        UUID id = register("CA", url);
        govern(id, body -> body.put("expectedIdentifiers", List.of("R156-60e")));
        fetcher.html(url, page(RULE_TEXT));
        runner.runOne();
        String baseline = (String) source(id).get("last_content_hash");
        long versions = ruleVersions();

        fetcher.html(url, page("The board has moved. Please visit the new site."));
        checkNow(id);
        assertThat(latestCheck(id)).containsEntry("outcome", "SUSPECT_CONTENT");
        assertThat(source(id)).containsEntry("last_content_hash", baseline);

        fetcher.html(url, page(RULE_TEXT));
        checkNow(id);
        assertThat(latestCheck(id)).as("the real text is still the baseline").containsEntry("outcome", "UNCHANGED");
        assertThat(ruleVersions()).isEqualTo(versions);
    }

    @Test
    void aSuspectCheckIsNotAFailureAndWaitsTheNormalInterval() {
        String url = newUrl("html");
        UUID id = register("CA", url);
        govern(id, body -> body.put("expectedIdentifiers", List.of("R156-60e")));
        fetcher.html(url, page("Nothing here."));

        runner.runOne();

        var schedule = inTenant(() -> jdbc.sql("SELECT consecutive_failures, next_check_at > now() + interval '23 hours' AS waits_the_interval "
                + "FROM rule_source WHERE id = :id").param("id", id).query().singleRow());
        assertThat(schedule).containsEntry("consecutive_failures", 0).containsEntry("waits_the_interval", true);
    }

    @Test
    void aPageWithAFormAndNoExpectedIdentifierLooksLikeALoginOrChallengePage() {
        String url = newUrl("html");
        UUID id = register("CA", url);
        fetcher.html(url, "<html><body><h1>Sign in</h1><form action=\"/login\"><input name=\"user\"></form>"
                + "<p>" + "Board of Examiners rules. ".repeat(100) + "</p></body></html>");

        runner.runOne();

        assertThat(latestCheck(id)).containsEntry("outcome", "SUSPECT_CONTENT");
        assertThat((String) latestCheck(id).get("detail")).contains("form");
    }

    @Test
    void aFormIsFineWhenTheExpectedIdentifiersAreThere() {
        String url = newUrl("html");
        UUID id = register("CA", url);
        govern(id, body -> body.put("expectedIdentifiers", List.of("R156-60e")));
        fetcher.html(url, "<html><body><form action=\"/search\"><input name=\"q\"></form><p>" + RULE_TEXT + "</p></body></html>");

        runner.runOne();

        assertThat(latestCheck(id)).containsEntry("outcome", "CHANGED");
    }

    @Test
    void aShortPageInTheShapeOfAChallengeIsSuspectButALongOneMentioningItIsNot() {
        String challenge = newUrl("html");
        UUID blocked = register("CA", challenge);
        fetcher.html(challenge, page("Just a moment...", "Checking your browser before accessing the site."));
        runner.runOne();
        assertThat(latestCheck(blocked)).containsEntry("outcome", "SUSPECT_CONTENT");
        assertThat((String) latestCheck(blocked).get("detail")).contains("short").contains("block or challenge");

        String rules = newUrl("html");
        UUID legitimate = register("TX", rules);
        fetcher.html(rules, page("The applicant must not use a captcha to hide a record. " + "Fictional rule text for the board. ".repeat(80)));
        runner.runOne();
        assertThat(latestCheck(legitimate)).containsEntry("outcome", "CHANGED");
    }

    @Test
    void aPageThatIsNotTheFormatTheAddressPromisesIsSuspect() {
        String pdf = newUrl("pdf");
        UUID id = register("CA", pdf);
        fetcher.html(pdf, page("Please enable JavaScript and cookies to continue."));

        runner.runOne();

        var check = latestCheck(id);
        assertThat(check).containsEntry("outcome", "SUSPECT_CONTENT");
        assertThat((String) check.get("detail")).contains("ends in .pdf").contains("HTML");
    }

    @Test
    void aContentTypeNoneOfOurReadersHandlesIsSuspectNotAFailure() {
        String url = "https://" + FakeHostResolver.PUBLIC_HOST + "/" + UUID.randomUUID();
        UUID id = register("CA", url);
        fetcher.respond(url, 200, "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});

        runner.runOne();

        var check = latestCheck(id);
        assertThat(check).containsEntry("outcome", "SUSPECT_CONTENT");
        assertThat((String) check.get("detail")).startsWith("Unsupported content type image/png");
        assertThat(ruleVersions()).isZero();
    }

    @Test
    void aTextFileAtATxtAddressIsAccepted() {
        String url = newUrl("txt");
        UUID id = register("CA", url);
        fetcher.respond(url, 200, "text/plain; charset=utf-8", "Applicants need 66 semester hours.".getBytes(StandardCharsets.UTF_8));

        runner.runOne();

        assertThat(latestCheck(id)).containsEntry("outcome", "CHANGED");
    }

    @Test
    void anUnreadableBodyIsStillAFailureNotSuspicion() {
        String url = newUrl("pdf");
        UUID id = register("CA", url);
        fetcher.respond(url, 200, "application/pdf", "%PDF-1.7 this is not a pdf".getBytes(StandardCharsets.UTF_8));

        runner.runOne();

        assertThat(latestCheck(id)).containsEntry("outcome", "FETCH_FAILED");
    }

    @Test
    void theSuspectOutcomeAndTheAccessStatusAreInTheCheckList() {
        String url = newUrl("html");
        UUID id = register("CA", url);
        fetcher.html(url, page("Access denied."));
        runner.runOne();

        var checks = body(mvc.get().uri("/api/v1/rule-sources/" + id + "/checks").with(user(t.reviewer())).exchange());

        assertThat(checks.get(0).get("outcome").asString()).isEqualTo("SUSPECT_CONTENT");
        assertThat(checks.get(0).get("accessStatus").asString()).isEqualTo("UNREVIEWED");
    }
}
