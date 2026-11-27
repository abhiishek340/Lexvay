package com.lexvay.observability;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.assessment.ModelCallScenario;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.web.client.RestClient;

/** A pipeline run produces the documented meters, and the scrape on the management port shows them. */
class MetricsTest extends AbstractAssessmentTest {

    private static final Set<String> ALLOWED_LABELS = Set.of("purpose", "model", "outcome", "direction", "type", "result",
            "finding", "method");

    @Autowired MeterRegistry registry;
    @LocalManagementPort int managementPort;

    private double counter(String name, String... tags) {
        var counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private long timerCount(String name, String... tags) {
        var timer = registry.find(name).tags(tags).timer();
        return timer == null ? 0 : timer.count();
    }

    private double queueDepth() {
        return registry.get("lexvay.job.queue.depth").gauge().value();
    }

    private String scrape() {
        return RestClient.create("http://localhost:" + managementPort).get().uri("/actuator/prometheus")
                .retrieve().body(String.class);
    }

    @Test
    void aPipelineRunCountsCallsTokensQuotesFindingsAndTheJob() {
        double calls = counter("lexvay.llm.calls", "purpose", "MATCH_EVIDENCE", "model", "fake-llm", "outcome", "success");
        double extractCalls = counter("lexvay.llm.calls", "purpose", "EXTRACT_REQUIREMENTS", "model", "fake-llm", "outcome", "success");
        double input = counter("lexvay.llm.tokens", "direction", "input");
        double output = counter("lexvay.llm.tokens", "direction", "output");
        long durations = timerCount("lexvay.llm.duration");
        double verified = counter("lexvay.quote.verification", "result", "verified");
        double rejected = counter("lexvay.quote.verification", "result", "rejected");
        double found = counter("lexvay.mapping.finding", "finding", "EVIDENCE_FOUND", "method", "MATCH");
        double missing = counter("lexvay.mapping.finding", "finding", "EVIDENCE_MISSING", "method", "MATCH");
        long assessmentJobs = timerCount("lexvay.job.duration", "type", "RUN_ASSESSMENT", "outcome", "done");

        var run = ModelCallScenario.run(fx, fake, t);

        assertThat(counter("lexvay.llm.calls", "purpose", "MATCH_EVIDENCE", "model", "fake-llm", "outcome", "success") - calls).isEqualTo(2);
        assertThat(counter("lexvay.llm.calls", "purpose", "EXTRACT_REQUIREMENTS", "model", "fake-llm", "outcome", "success") - extractCalls).isEqualTo(1);
        assertThat(timerCount("lexvay.llm.duration") - durations).isEqualTo(3);
        assertThat(counter("lexvay.llm.tokens", "direction", "input") - input).isGreaterThanOrEqualTo(run.inputTokens());
        assertThat(counter("lexvay.llm.tokens", "direction", "output") - output).isGreaterThanOrEqualTo(run.outputTokens());
        assertThat(counter("lexvay.quote.verification", "result", "verified") - verified).isEqualTo(1);
        assertThat(counter("lexvay.quote.verification", "result", "rejected") - rejected).isEqualTo(1);
        assertThat(counter("lexvay.mapping.finding", "finding", "EVIDENCE_FOUND", "method", "MATCH") - found).isEqualTo(1);
        assertThat(counter("lexvay.mapping.finding", "finding", "EVIDENCE_MISSING", "method", "MATCH") - missing).isEqualTo(1);
        assertThat(timerCount("lexvay.job.duration", "type", "RUN_ASSESSMENT", "outcome", "done") - assessmentJobs).isEqualTo(1);
    }

    @Test
    void theQueueDepthGaugeCountsQueuedJobsAcrossTenants() {
        assertThat(queueDepth()).isZero();
        UUID programId = fx.program(t, List.of("UT"));
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah queue", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "60 hours"))));
        var started = mvc.post().uri("/api/v1/programs/" + programId + "/assessments").with(user(t.reviewer())).with(csrf()).exchange();
        assertThat(started).hasStatus(202);

        assertThat(queueDepth()).isEqualTo(1);
        runner.runPending();
        assertThat(queueDepth()).isZero();
    }

    @Test
    void theScrapeShowsTheMetricsWithBoundedLabelsAndNoContent() {
        var run = ModelCallScenario.run(fx, fake, t);
        String body = scrape();

        assertThat(body).contains("lexvay_llm_calls_total{", "lexvay_llm_tokens_total{", "lexvay_llm_duration_seconds_count",
                "lexvay_job_duration_seconds_count{", "lexvay_job_queue_depth ", "lexvay_quote_verification_total{",
                "lexvay_mapping_finding_total{");
        assertThat(body).contains("outcome=\"success\"", "direction=\"input\"", "result=\"rejected\"", "type=\"RUN_ASSESSMENT\"");

        Matcher labels = Pattern.compile("^lexvay_[a-z_]+\\{([^}]*)\\}", Pattern.MULTILINE).matcher(body);
        int lines = 0;
        while (labels.find()) {
            lines++;
            Matcher key = Pattern.compile("([a-z_]+)=\"").matcher(labels.group(1));
            while (key.find()) {
                assertThat(ALLOWED_LABELS).as("label on: " + labels.group()).contains(key.group(1));
            }
        }
        assertThat(lines).isPositive();

        assertThat(body).doesNotContain(run.assessmentId().toString()).doesNotContain(t.tenantId().toString())
                .doesNotContain(t.reviewer().email()).doesNotContain(ModelCallScenario.FABRICATED_QUOTE)
                .doesNotContain("legal standards").doesNotContain("telehealth");
    }

    @Test
    void metricTagsAreNeverIdentifiers() {
        ModelCallScenario.run(fx, fake, t);
        Pattern uuid = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        registry.getMeters().stream().filter(m -> m.getId().getName().startsWith("lexvay."))
                .flatMap(m -> m.getId().getTags().stream()).map(Tag::getValue)
                .forEach(value -> assertThat(uuid.matcher(value).find()).as(value).isFalse());
    }
}
