package com.lexvay.evidencepackage;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.audit.AuditEntry;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestCsv;
import com.lexvay.determination.DeterminationRepository;
import com.lexvay.document.DocumentService;
import com.lexvay.review.ReviewFixtures;
import com.lexvay.tenant.DataMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class PackageApiTest extends AbstractAssessmentTest {

    @Autowired JdbcClient jdbc;
    @Autowired AuditRepository audit;
    @Autowired TransactionTemplate transaction;
    @Autowired PackageSnapshotLoader snapshots;
    @Autowired PackageBuilder builder;
    @Autowired DeterminationRepository determinations;

    PackageFixtures packages;
    ReviewFixtures.Scenario scenario;
    UUID determinationId;

    @BeforeEach
    void signedDetermination() {
        packages = new PackageFixtures(mvc, json, runner);
        var rf = new ReviewFixtures(mvc, json, fx);
        scenario = rf.californiaScenario(t, "package");
        rf.decideAll(t, scenario);
        determinationId = UUID.fromString(fx.read(rf.sign(t.signer(), scenario.programId(), "CA", "DOES_NOT_MEET")).get("id").asString());
    }

    private List<AuditEntry> auditOf(UUID entityId) {
        return TenantContext.runAs(t.tenantId(), () -> audit.forEntity(entityId));
    }

    @Test
    void aSignedDeterminationIsPackagedAndEveryStepIsAudited() throws Exception {
        UUID reportId = packages.readyReport(t.signer(), determinationId);
        UUID packageId = packages.acceptedPackage(t.signer(), determinationId);

        JsonNode queued = packages.status(t.reviewer(), packageId);
        assertThat(queued.get("status").asString()).isEqualTo("QUEUED");
        assertThat(queued.get("sizeBytes").isNull()).isTrue();
        assertThat(packages.file(t.reviewer(), packageId)).as("not ready yet").hasStatus(409);

        runner.runPending();

        JsonNode ready = packages.status(t.reviewer(), packageId);
        assertThat(ready.get("status").asString()).isEqualTo("READY");
        MvcTestResult download = packages.file(t.reviewer(), packageId);
        assertThat(download).hasStatus(200);
        assertThat(download.getResponse().getContentType()).isEqualTo("application/zip");
        assertThat(download.getResponse().getHeader("Content-Disposition"))
                .matches("attachment; filename=\"lexvay-m-s-clinical-mental-health-counseling-ca-\\d{4}-\\d{2}-\\d{2}\\.zip\"");
        byte[] zip = download.getResponse().getContentAsByteArray();
        assertThat(zip).hasSize(ready.get("sizeBytes").asInt());
        assertThat(DocumentService.sha256(zip)).as("the stored ZIP's hash").isEqualTo(ready.get("sha256").asString());

        Map<String, byte[]> entries = PackageFixtures.unzip(zip);
        assertThat(entries.keySet()).containsExactly("README.txt", "report.pdf", "determination.json", "requirements.csv",
                "quotes.csv", "audit.csv", "documents/coun605.txt", "documents/degree-plan.txt", "manifest.json");
        JsonNode manifest = packages.checkedManifest(entries);
        assertThat(manifest.get("packageId").asString()).isEqualTo(packageId.toString());
        assertThat(manifest.get("determinationId").asString()).isEqualTo(determinationId.toString());
        assertThat(manifest.get("dataMode").asString()).isEqualTo("DEMO");
        assertThat(manifest.get("lexvayVersion").asString()).isNotBlank();
        assertThat(Instant.parse(manifest.get("generatedAt").asString())).isBefore(Instant.now().plusSeconds(1));

        byte[] storedReport = mvc.get().uri("/api/v1/reports/" + reportId).with(user(t.reviewer())).exchange()
                .getResponse().getContentAsByteArray();
        assertThat(entries.get("report.pdf")).as("the report's exact bytes").isEqualTo(storedReport);
        assertThat(new String(entries.get("documents/coun605.txt"), StandardCharsets.UTF_8)).isEqualTo(SYLLABUS_605);
        String readme = new String(entries.get("README.txt"), StandardCharsets.UTF_8);
        assertThat(readme).as("a DEMO tenant's README says in its first line that the data is fictional").startsWith("Fictional demo data.\n");
        assertThat(readme.substring("Fictional demo data.\n".length()).split("\\.\\s+|\\.\\n")).hasSize(4);

        JsonNode determination = json.readTree(entries.get("determination.json"));
        assertThat(determination.get("id").asString()).isEqualTo(determinationId.toString());
        assertThat(determination.get("program").get("name").asString()).isEqualTo("M.S. Clinical Mental Health Counseling");
        assertThat(determination.get("state").get("code").asString()).isEqualTo("CA");
        assertThat(determination.get("outcome").asString()).isEqualTo("DOES_NOT_MEET");
        assertThat(determination.get("signer").get("name").asString()).isEqualTo(t.signer().displayName());
        assertThat(determination.get("status").asString()).isEqualTo("SIGNED");
        assertThat(determination.get("versionsHash").asString()).matches("[0-9a-f]{64}");
        assertThat(determination.get("ruleVersion").get("id").asString()).isEqualTo(scenario.ruleVersionId().toString());
        assertThat(determination.get("ruleVersion").get("label").asString()).isEqualTo("Demo rule set California package");
        assertThat(determination.get("ruleVersion").get("effectiveFrom").asString()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(determination.get("ruleVersion").get("legalStatus").asString()).isEqualTo("IN_FORCE");
        assertThat(determination.get("ruleVersion").get("datesBasis").asString()).isNotBlank();
        assertThat(determination.get("courseVersions")).extracting(c -> c.get("code").asString()).containsExactly("COUN 605", "COUN 650");
        assertThat(determination.get("courseVersions")).extracting(c -> c.get("id").asString())
                .containsExactlyInAnyOrder(scenario.course605Version().toString(), scenario.course650Version().toString());

        List<List<String>> requirements = TestCsv.parse(new String(entries.get("requirements.csv"), StandardCharsets.UTF_8));
        assertThat(requirements.get(0)).isEqualTo(PackageEntries.REQUIREMENT_COLUMNS);
        assertThat(requirements).hasSize(4);
        assertThat(requirements.get(1)).startsWith("CA-1").contains("ACCEPTED").contains(t.reviewer().displayName());
        assertThat(requirements.get(2)).startsWith("CA-2").contains("EVIDENCE_MISSING", "GAP_CONFIRMED",
                "No telehealth content anywhere in the curriculum.");
        assertThat(requirements.get(3)).startsWith("CA-3").contains("EVIDENCE_REQUESTED");

        List<List<String>> quotes = TestCsv.parse(new String(entries.get("quotes.csv"), StandardCharsets.UTF_8));
        assertThat(quotes.get(0)).isEqualTo(PackageEntries.QUOTE_COLUMNS);
        assertThat(quotes).hasSizeGreaterThan(1);
        for (List<String> quote : quotes.subList(1, quotes.size())) {
            assertThat(entries).containsKey("documents/" + quote.get(1));
            assertThat(quote.get(6)).as("page hash of %s page %s", quote.get(1), quote.get(2)).isEqualTo(storedPageHash(quote.get(1), quote.get(2)));
        }

        List<AuditEntry> trail = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(determinationId));
        assertThat(trail).extracting(AuditEntry::action).contains("DETERMINATION_SIGNED");
        List<String> auditActions = TestCsv.parse(new String(entries.get("audit.csv"), StandardCharsets.UTF_8)).stream()
                .skip(1).map(row -> row.get(2)).toList();
        assertThat(auditActions).contains("DETERMINATION_SIGNED", "REVIEW_RECORDED", "REPORT_REQUESTED", "REPORT_GENERATED",
                "PACKAGE_REQUESTED").doesNotContain("PACKAGE_READY", "PACKAGE_DOWNLOADED", "LOGIN_SUCCEEDED");
        assertThat(auditActions.stream().filter("REVIEW_RECORDED"::equals)).hasSize(3);
        assertThat(TestCsv.parse(new String(entries.get("audit.csv"), StandardCharsets.UTF_8)).get(0))
                .as("the package's trail carries the seal columns of the audit export (SEC-3)")
                .containsSequence("Seq", "Row hash", "Entry ID", "Actor ID");

        List<AuditEntry> packageTrail = auditOf(packageId);
        assertThat(packageTrail).extracting(AuditEntry::action).containsExactly("PACKAGE_DOWNLOADED", "PACKAGE_READY", "PACKAGE_REQUESTED");
        assertThat(packageTrail).extracting(AuditEntry::userId).containsExactly(t.reviewer().id(), null, t.signer().id());
        assertThat(packageTrail.get(1).detail()).containsEntry("sha256", ready.get("sha256").asString())
                .containsEntry("determinationId", determinationId.toString());
    }

    private String storedPageHash(String filename, String page) {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql("""
                SELECT encode(sha256(convert_to(dp.text, 'UTF8')), 'hex') FROM document_page dp
                JOIN document d ON d.id = dp.document_id
                WHERE d.filename = :filename AND d.program_id = :program AND dp.page_number = :page
                """).param("filename", filename).param("program", scenario.programId()).param("page", Integer.parseInt(page))
                .query(String.class).single());
    }

    @Test
    void aDeterminationWithoutAReadyReportIsRefused() {
        assertThat(packages.requestPackage(t.signer(), determinationId)).hasStatus(409)
                .bodyText().contains("Generate the report first");

        UUID reportId = UUID.fromString(fx.read(mvc.post().uri("/api/v1/determinations/" + determinationId + "/report")
                .with(user(t.signer())).with(csrf()).exchange()).get("reportId").asString());
        assertThat(packages.requestPackage(t.signer(), determinationId)).as("the report is still queued").hasStatus(409);

        runner.runPending();
        assertThat(mvc.get().uri("/api/v1/reports/" + reportId).with(user(t.signer())).exchange()).hasStatus(200);
        assertThat(packages.requestPackage(t.signer(), determinationId)).hasStatus(202);
    }

    @Test
    void aReopenedDeterminationIsRefused() {
        packages.readyReport(t.signer(), determinationId);
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("UPDATE determination SET status = 'REOPENED' WHERE id = :id")
                .param("id", determinationId).update());

        assertThat(packages.requestPackage(t.signer(), determinationId)).hasStatus(409).bodyText().contains("signed determination");
    }

    @Test
    void anotherTenantFindsNothing() {
        packages.readyReport(t.signer(), determinationId);
        UUID packageId = packages.acceptedPackage(t.signer(), determinationId);
        runner.runPending();
        var other = tenants.create("other");

        assertThat(packages.requestPackage(other.signer(), determinationId)).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/packages/" + packageId).with(user(other.admin())).exchange()).hasStatus(404);
        assertThat(packages.file(other.admin(), packageId)).hasStatus(404);
        assertThat(packages.file(t.admin(), packageId)).hasStatus(200);
    }

    @Test
    void aPackageOfATenantWithRealDataSaysSoInItsManifestAndLeavesTheReadmeAsItIs() throws Exception {
        packages.readyReport(t.signer(), determinationId);
        UUID packageId = packages.acceptedPackage(t.signer(), determinationId);
        tenants.setDataMode(t.tenantId(), DataMode.LIVE);
        Instant generatedAt = Instant.parse("2026-11-27T10:15:30Z");

        Built first = build(packageId, generatedAt);

        Map<String, byte[]> entries = PackageFixtures.unzip(first.bytes());
        assertThat(packages.checkedManifest(entries).get("dataMode").asString()).isEqualTo("LIVE");
        assertThat(entries.get("README.txt")).isEqualTo(new ClassPathResource("evidence-package/README.txt").getContentAsByteArray());
        assertThat(new String(entries.get("README.txt"), StandardCharsets.UTF_8)).doesNotContain("Fictional demo data");
        assertThat(build(packageId, generatedAt).bytes()).as("still deterministic").isEqualTo(first.bytes());
    }

    @Test
    void buildingTwiceWithTheSameClockGivesTheSameBytes() throws Exception {
        packages.readyReport(t.signer(), determinationId);
        UUID packageId = packages.acceptedPackage(t.signer(), determinationId);
        Instant generatedAt = Instant.parse("2026-11-27T10:15:30Z");

        Built first = build(packageId, generatedAt);
        Built second = build(packageId, generatedAt);

        assertThat(second.bytes()).isEqualTo(first.bytes());
        assertThat(first.result().sha256()).as("the hash is over the stored bytes").isEqualTo(DocumentService.sha256(first.bytes()));
        assertThat(first.result().sizeBytes()).isEqualTo(first.bytes().length);
        assertThat(build(packageId, generatedAt.plusSeconds(60)).result().sha256())
                .as("the manifest's generatedAt is part of the bytes").isNotEqualTo(first.result().sha256());
    }

    private record Built(PackageBuilder.Built result, byte[] bytes) {
    }

    private Built build(UUID packageId, Instant generatedAt) throws Exception {
        Path target = Files.createTempFile("package-", ".zip");
        try {
            PackageBuilder.Built result = TenantContext.runAs(t.tenantId(), () -> transaction.execute(status -> {
                try {
                    return builder.build(snapshots.load(determinations.findById(determinationId).orElseThrow()), packageId, generatedAt, target);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));
            return new Built(result, Files.readAllBytes(target));
        } finally {
            Files.deleteIfExists(target);
        }
    }
}
