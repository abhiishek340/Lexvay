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
