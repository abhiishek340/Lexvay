package com.lexvay.report;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.document.FileType;
import com.lexvay.document.NoOcrEngine;
import com.lexvay.document.TextExtractor;
import com.lexvay.review.ReviewFixtures;
import com.lexvay.tenant.DataMode;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReportTest extends AbstractAssessmentTest {

    @Test
    void signedDeterminationReportIsGeneratedAndDownloadable() throws Exception {
        var rf = new ReviewFixtures(mvc, json, fx);
        var s = rf.californiaScenario(t, "report");
        rf.decideAll(t, s);
        UUID determinationId = UUID.fromString(fx.read(rf.sign(t.signer(), s.programId(), "CA", "DOES_NOT_MEET")).get("id").asString());

        var requested = mvc.post().uri("/api/v1/determinations/" + determinationId + "/report").with(user(t.signer())).with(csrf()).exchange();
        assertThat(requested).hasStatus(202);
        UUID reportId = UUID.fromString(fx.read(requested).get("reportId").asString());

        var pending = mvc.get().uri("/api/v1/reports/" + reportId).with(user(t.reviewer())).exchange();
        assertThat(pending).hasStatus(202);
        assertThat(fx.read(pending).get("status").asString()).isEqualTo("QUEUED");

        runner.runPending();

        var ready = mvc.get().uri("/api/v1/reports/" + reportId).with(user(t.reviewer())).exchange();
        assertThat(ready).hasStatus(200);
        assertThat(ready.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(ready.getResponse().getHeader("Content-Disposition")).contains("lexvay-evidence-report-");
        byte[] pdf = ready.getResponse().getContentAsByteArray();
        String text = new TextExtractor(new NoOcrEngine(), 200).extract(FileType.PDF, pdf).stream().map(p -> p.text()).collect(Collectors.joining("\n"));
        assertThat(text).contains("Signed determination").contains("Determined not to meet").contains(t.signer().displayName())
                .contains("CA-1").contains("CA-2").contains("CA-3").contains("Gap confirmed").contains("ACA Code of Ethics")
                .contains("Versions hash").contains("Data: Fictional demo data").doesNotContainIgnoringCase("compliant");

        var other = tenants.create("other");
        assertThat(mvc.get().uri("/api/v1/reports/" + reportId).with(user(other.admin())).exchange()).hasStatus(404);
        assertThat(mvc.post().uri("/api/v1/determinations/" + UUID.randomUUID() + "/report").with(user(t.signer())).with(csrf()).exchange()).hasStatus(404);
    }

    /**
     * D-515: the determination read model names its newest READY report, so a signer who returns to the report screen
     * downloads the stored PDF and requests the package without generating again. A queued report is not ready, and
     * a newer ready one replaces the older in the read model.
     */
    @Test
    void theDeterminationNamesItsNewestReadyReport() throws Exception {
        var rf = new ReviewFixtures(mvc, json, fx);
        var s = rf.californiaScenario(t, "report-latest");
        rf.decideAll(t, s);
        UUID determinationId = UUID.fromString(fx.read(rf.sign(t.signer(), s.programId(), "CA", "DOES_NOT_MEET")).get("id").asString());
        String list = "/api/v1/programs/" + s.programId() + "/determinations";

        var none = fx.read(mvc.get().uri(list).with(user(t.reviewer())).exchange()).get(0);
        assertThat(none.get("latestReadyReportId").isNull()).isTrue();
        assertThat(none.get("latestReadyReportAt").isNull()).isTrue();

        String first = requestReport(determinationId);
        assertThat(fx.read(mvc.get().uri(list).with(user(t.reviewer())).exchange()).get(0).get("latestReadyReportId").isNull())
                .as("a queued report is not ready").isTrue();
        runner.runPending();
        var ready = fx.read(mvc.get().uri(list).with(user(t.reviewer())).exchange()).get(0);
        assertThat(ready.get("latestReadyReportId").asString()).isEqualTo(first);
        assertThat(ready.get("latestReadyReportAt").isNull()).isFalse();

        String second = requestReport(determinationId);
        runner.runPending();
        assertThat(fx.read(mvc.get().uri(list).with(user(t.reviewer())).exchange()).get(0).get("latestReadyReportId").asString()).isEqualTo(second);

        var overview = fx.read(mvc.get().uri("/api/v1/programs/" + s.programId() + "/overview").with(user(t.reviewer())).exchange());
        assertThat(overview.get("determinations").get(0).get("latestReadyReportId").asString()).isEqualTo(second);
        // The stored PDF it names is the one a returning signer downloads, and the package needs nothing more.
        assertThat(mvc.get().uri("/api/v1/reports/" + second).with(user(t.signer())).exchange()).hasStatus(200);
        assertThat(mvc.post().uri("/api/v1/determinations/" + determinationId + "/package").with(user(t.signer())).with(csrf()).exchange()).hasStatus(202);
    }

    private String requestReport(UUID determinationId) throws Exception {
        var requested = mvc.post().uri("/api/v1/determinations/" + determinationId + "/report").with(user(t.signer())).with(csrf()).exchange();
        assertThat(requested).hasStatus(202);
        return fx.read(requested).get("reportId").asString();
    }

    @Test
    void aReportOfATenantWithRealDataHasNoDemoNotice() throws Exception {
        var rf = new ReviewFixtures(mvc, json, fx);
        var s = rf.californiaScenario(t, "report-live");
        rf.decideAll(t, s);
        UUID determinationId = UUID.fromString(fx.read(rf.sign(t.signer(), s.programId(), "CA", "DOES_NOT_MEET")).get("id").asString());
        tenants.setDataMode(t.tenantId(), DataMode.LIVE);

        var requested = mvc.post().uri("/api/v1/determinations/" + determinationId + "/report").with(user(t.signer())).with(csrf()).exchange();
        assertThat(requested).hasStatus(202);
        UUID reportId = UUID.fromString(fx.read(requested).get("reportId").asString());
        runner.runPending();

        var ready = mvc.get().uri("/api/v1/reports/" + reportId).with(user(t.reviewer())).exchange();
        assertThat(ready).hasStatus(200);
        String text = new TextExtractor(new NoOcrEngine(), 200).extract(FileType.PDF, ready.getResponse().getContentAsByteArray())
                .stream().map(p -> p.text()).collect(Collectors.joining("\n"));
        assertThat(text).contains("Signed determination").contains("Versions hash").doesNotContain("Fictional demo data");
    }
}
