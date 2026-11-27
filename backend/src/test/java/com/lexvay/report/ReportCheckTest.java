package com.lexvay.report;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.auth.LexvayUser;
import com.lexvay.common.TenantContext;
import com.lexvay.document.DocumentService;
import com.lexvay.review.ReviewFixtures;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** F14: a report file handed back is looked up by the SHA-256 of its bytes, within the caller's tenant. */
class ReportCheckTest extends AbstractAssessmentTest {

    @Autowired JdbcClient jdbc;

    ReviewFixtures rf;
    ReviewFixtures.Scenario s;
    UUID determinationId;
    UUID reportId;
    byte[] pdf;

    @BeforeEach
    void signAndGenerateAReport() throws Exception {
        rf = new ReviewFixtures(mvc, json, fx);
        s = rf.californiaScenario(t, "check");
        rf.decideAll(t, s);
        determinationId = UUID.fromString(fx.read(rf.sign(t.signer(), s.programId(), "CA", "DOES_NOT_MEET")).get("id").asString());
        var requested = mvc.post().uri("/api/v1/determinations/" + determinationId + "/report").with(user(t.signer())).with(csrf()).exchange();
        reportId = UUID.fromString(fx.read(requested).get("reportId").asString());
        runner.runPending();
        var downloaded = mvc.get().uri("/api/v1/reports/" + reportId).with(user(t.reviewer())).exchange();
        assertThat(downloaded).hasStatus(200);
        pdf = downloaded.getResponse().getContentAsByteArray();
    }

    private MvcTestResult check(LexvayUser as, byte[] bytes) {
        return mvc.perform(multipart("/api/v1/reports/check")
                .file(new MockMultipartFile("file", "report.pdf", "application/pdf", bytes)).with(user(as)).with(csrf()));
    }

    /** The REPORT_CHECKED entries of a tenant, newest first, as {entity_id, detail}. */
    private java.util.List<Map<String, Object>> checkEntries(UUID tenantId) {
        return TenantContext.runAs(tenantId, () -> jdbc.sql(
                "SELECT entity_type, entity_id, user_id, detail::text AS detail FROM audit_log WHERE action = 'REPORT_CHECKED' ORDER BY created_at DESC, id DESC")
                .query().listOfRows());
    }

    private JsonNode detail(Map<String, Object> entry) {
        return json.readTree((String) entry.get("detail"));
    }

    @Test
    void theDownloadedFileMatchesItsReportWithEveryFieldAndTheCheckIsAudited() throws Exception {
        String sha256 = DocumentService.sha256(pdf);
        assertThat(TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT content_sha256 FROM report WHERE id = :id")
                .param("id", reportId).query(String.class).single())).as("hashed in the update that marks it ready").isEqualTo(sha256);

        var result = check(t.contributor(), pdf);

        assertThat(result).hasStatus(200);
        JsonNode body = fx.read(result);
        assertThat(body.get("match").asBoolean()).isTrue();
        assertThat(body.get("sha256").asString()).isEqualTo(sha256);
        JsonNode m = body.get("matchDetails");
        OffsetDateTime generatedAt = TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT created_at FROM report WHERE id = :id")
                .param("id", reportId).query(OffsetDateTime.class).single());
        var determination = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT d.signed_at, d.versions_hash, rv.label FROM determination d JOIN rule_version rv ON rv.id = d.rule_version_id WHERE d.id = :id")
                .param("id", determinationId).query().singleRow());
        assertThat(m.get("reportId").asString()).isEqualTo(reportId.toString());
        assertThat(OffsetDateTime.parse(m.get("generatedAt").asString())).isEqualTo(generatedAt);
        assertThat(m.get("determinationId").asString()).isEqualTo(determinationId.toString());
        assertThat(m.get("programId").asString()).isEqualTo(s.programId().toString());
        assertThat(m.get("programName").asString()).isEqualTo(fx.read(mvc.get().uri("/api/v1/programs/" + s.programId())
                .with(user(t.reviewer())).exchange()).get("name").asString());
        assertThat(m.get("jurisdictionCode").asString()).isEqualTo("CA");
        assertThat(m.get("ruleVersionLabel").asString()).isEqualTo(determination.get("label")).isEqualTo("Demo rule set California check");
        assertThat(m.get("outcome").asString()).isEqualTo("DOES_NOT_MEET");
        assertThat(m.get("signedByName").asString()).isEqualTo(t.signer().displayName());
        assertThat(OffsetDateTime.parse(m.get("signedAt").asString()).toInstant())
                .isEqualTo(((java.sql.Timestamp) determination.get("signed_at")).toInstant());
        assertThat(m.get("determinationStatus").asString()).isEqualTo("SIGNED");
        assertThat(m.get("reopenedAt").isNull()).isTrue();
        assertThat(m.get("versionsHash").asString()).isEqualTo(determination.get("versions_hash"));

        var entries = checkEntries(t.tenantId());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("entity_type")).isEqualTo("report");
        assertThat(entries.get(0).get("entity_id")).isEqualTo(reportId);
        assertThat(entries.get(0).get("user_id")).isEqualTo(t.contributor().id());
        assertThat(detail(entries.get(0)).get("match").asBoolean()).isTrue();
        assertThat(detail(entries.get(0)).get("sha256").asString()).isEqualTo(sha256);
    }

    @Test
    void oneChangedByteIsNoMatchAndIsAuditedWithoutARecord() throws Exception {
        byte[] changed = pdf.clone();
        changed[changed.length / 2] ^= 1;

        var result = check(t.reviewer(), changed);

        assertThat(result).hasStatus(200);
        JsonNode body = fx.read(result);
        assertThat(body.get("match").asBoolean()).isFalse();
        assertThat(body.get("sha256").asString()).isEqualTo(DocumentService.sha256(changed));
        assertThat(body.get("matchDetails").isNull()).isTrue();
        var entries = checkEntries(t.tenantId());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("entity_type")).isEqualTo("report");
        assertThat(entries.get(0).get("entity_id")).isNull();
        assertThat(detail(entries.get(0)).get("match").asBoolean()).isFalse();
        assertThat(detail(entries.get(0)).get("sha256").asString()).isEqualTo(DocumentService.sha256(changed));
    }

    @Test
    void theReportOfAnotherTenantIsNoMatch() throws Exception {
        var other = tenants.create("check-other");

        var result = check(other.admin(), pdf);

        assertThat(result).hasStatus(200);
        assertThat(fx.read(result).get("match").asBoolean()).as("rule 7: the hash exists only in the first tenant").isFalse();
        assertThat(fx.read(result).get("matchDetails").isNull()).isTrue();
        assertThat(checkEntries(other.tenantId())).singleElement()
                .satisfies(entry -> assertThat(detail(entry).get("match").asBoolean()).isFalse());
        assertThat(checkEntries(t.tenantId())).as("nothing reaches the tenant that owns the report").isEmpty();
    }

    @Test
    void aReopenedDeterminationIsReportedAsReopenedWithItsTime() throws Exception {
        JsonNode courses = fx.read(mvc.get().uri("/api/v1/programs/" + s.programId() + "/courses").with(user(t.reviewer())).exchange());
        String course605 = null;
        for (JsonNode course : courses) {
            if (course.get("code").asString().equals("COUN 605")) {
                course605 = course.get("id").asString();
            }
        }
        assertThat(mvc.post().uri("/api/v1/courses/" + course605 + "/versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("catalogYear", "2027-28", "required", true)))
                .exchange()).hasStatus(201);
        OffsetDateTime reopenedAt = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT created_at FROM audit_log WHERE action = 'DETERMINATION_REOPENED' AND entity_id = :id")
                .param("id", determinationId).query(OffsetDateTime.class).single());

        var result = check(t.signer(), pdf);

        assertThat(result).hasStatus(200);
        JsonNode m = fx.read(result).get("matchDetails");
        assertThat(fx.read(result).get("match").asBoolean()).isTrue();
        assertThat(m.get("determinationId").asString()).isEqualTo(determinationId.toString());
        assertThat(m.get("determinationStatus").asString()).isEqualTo("REOPENED");
        assertThat(OffsetDateTime.parse(m.get("reopenedAt").asString())).isEqualTo(reopenedAt);
        assertThat(checkEntries(t.tenantId())).singleElement().satisfies(entry -> {
            assertThat(entry.get("entity_id")).isEqualTo(reportId);
            assertThat(detail(entry).get("match").asBoolean()).isTrue();
        });
    }

    @Test
    void aFileThatIsNotAPdfIsRefusedAndNothingIsAudited() throws Exception {
        for (byte[] body : new byte[][] {"Lexvay evidence report".getBytes(), new byte[0], "%PDF".getBytes()}) {
            var result = check(t.reviewer(), body);
            assertThat(result).hasStatus(400);
            assertThat(result.getResponse().getContentType()).contains("application/problem+json");
            assertThat(fx.read(result).get("detail").asString()).isEqualTo("Not a PDF file");
        }
        assertThat(checkEntries(t.tenantId())).isEmpty();
    }
}
