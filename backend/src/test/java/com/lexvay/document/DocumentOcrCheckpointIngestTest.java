package com.lexvay.document;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.lexvay.audit.AuditRepository;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.SchemaFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.job.JobRunner;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** FIX-6: OCR results are committed page by page, survive a failed ingest attempt and are reused by the retry. */
class DocumentOcrCheckpointIngestTest extends AbstractDatabaseTest {

    static final List<String> TEXTS = List.of(
            "COUN 605 Professional Orientation and Ethical Practice. 3 credits.",
            "Week 3: Legal standards, confidentiality and duty to warn in counseling practice.",
            "Practicum: 100 hours of supervised clinical experience in an approved setting.",
            "Internship: 600 hours of supervised clinical experience across two terms.");

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired AuditRepository audit;
    @Autowired JobRunner runner;
    @Autowired DocumentRepository documents;
    @Autowired DocumentOcrCheckpoints checkpoints;
    @Autowired SchemaFixtures schema;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate transaction;

    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        runner.runPending(); // jobs other test classes left queued would be claimed before this test's
        t = tenants.create("ocr-checkpoints");
    }

    private UUID program(String name) throws Exception {
        var created = mvc.post().uri("/api/v1/programs").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", name, "licenseType", "L", "catalogYear", "2026-27", "jurisdictions", List.of("CA"))))
                .exchange();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asString());
    }

    /** Uploads and queues the ingest job without running it. */
    private UUID upload(UUID programId, byte[] pdf) throws Exception {
        var result = mvc.perform(multipart("/api/v1/programs/" + programId + "/documents")
                .file(new MockMultipartFile("file", "scan.pdf", "application/pdf", pdf))
                .param("kind", "SYLLABUS").with(user(t.contributor())).with(csrf()));
        assertThat(result).hasStatus(202);
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("documentId").asString());
    }

    private void scriptEngine() {
        ocr.enable();
        TEXTS.forEach(text -> ocr.page(text, 90));
    }

    private List<Integer> checkpointedPages(UUID documentId) {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT page_number FROM document_ocr_page WHERE document_id = :d ORDER BY page_number")
                .param("d", documentId).query(Integer.class).list());
    }

    private List<List<Object>> pagesOf(UUID documentId) {
        return TenantContext.runAs(t.tenantId(), () -> documents.pages(documentId)).stream()
                .map(p -> List.<Object>of(p.pageNumber(), p.text(), p.charStart(), p.charEnd(), p.textSource(), p.ocrConfidence()))
                .toList();
    }

    private Object ingestDetail(UUID documentId, String key) {
        return TenantContext.runAs(t.tenantId(), () -> audit.forEntity(documentId)).stream()
                .filter(e -> e.action().equals("DOCUMENT_INGESTED")).findFirst().orElseThrow().detail().get(key);
    }

    @Test
    void aFailureOnPageThreeKeepsPagesOneAndTwoAndTheRetryRecognisesOnlyTheRest() throws Exception {
        byte[] scan = TestDocuments.scannedPdf(java.util.Collections.nCopies(4, List.of("scanned page")), 72);
        scriptEngine();
        ocr.failOnCall(3);
        UUID documentId = upload(program("Checkpointed"), scan);

        assertThat(runner.runOne()).isTrue();
        assertThat(ocr.imagesSeen()).hasSize(3);
        assertThat(checkpointedPages(documentId)).as("committed although the attempt rolled back").containsExactly(1, 2);
        assertThat(pagesOf(documentId)).as("the failed attempt stored no page").isEmpty();

        assertThat(runner.runOne()).isTrue();
        assertThat(ocr.imagesSeen()).as("the retry recognised pages 3 and 4 only").hasSize(5);
        assertThat(checkpointedPages(documentId)).containsExactly(1, 2, 3, 4);
        assertThat(ingestDetail(documentId, "ocrPagesReused")).isEqualTo(2);
        assertThat(ingestDetail(documentId, "ocrPages")).isEqualTo(4);

        UUID cleanRun = upload(program("Clean run"), scan);
        TEXTS.forEach(text -> ocr.page(text, 90));
        runner.runPending();
        assertThat(pagesOf(documentId)).hasSize(4).isEqualTo(pagesOf(cleanRun));
        assertThat(ingestDetail(cleanRun, "ocrPagesReused")).isEqualTo(0);
    }

    @Test
    void aCheckpointCommitsEvenWhenTheEnclosingTransactionRollsBack() {
        UUID documentId = schema.insertChain(t.tenantId(), t.admin().id()).documentId();
        TenantContext.runAs(t.tenantId(), () -> {
            transaction.executeWithoutResult(status -> {
                checkpoints.forDocument(t.tenantId(), documentId).save(1, new OcrPage(TEXTS.get(0), 88.5), 300);
                status.setRollbackOnly();
            });
            return null;
        });

        var stored = TenantContext.runAs(t.tenantId(), () -> documents.ocrCheckpoints(documentId));
        assertThat(stored).containsExactly(new DocumentRepository.OcrCheckpoint(1, new OcrPage(TEXTS.get(0), 88.5), 300));
    }

    @Test
    void aCheckpointIsKeptAndNeverOverwrittenAndAnEmptyPageHasNoConfidence() {
        UUID documentId = schema.insertChain(t.tenantId(), t.admin().id()).documentId();
        TenantContext.runAs(t.tenantId(), () -> {
            var first = checkpoints.forDocument(t.tenantId(), documentId);
            first.save(1, new OcrPage(TEXTS.get(0), 88.5), 300);
            first.save(1, new OcrPage(TEXTS.get(1), 10), 150);
            first.save(2, OcrPage.NOTHING, 300);

            var again = checkpoints.forDocument(t.tenantId(), documentId);
            assertThat(again.find(1, 300)).contains(new OcrPage(TEXTS.get(0), 88.5));
            assertThat(again.find(1, 150)).isEmpty();
            assertThat(again.find(2, 300)).contains(OcrPage.NOTHING);
            assertThat(again.find(3, 300)).isEmpty();
            assertThat(again.reused()).isEqualTo(2);
            assertThat(jdbc.sql("SELECT mean_confidence IS NULL FROM document_ocr_page WHERE document_id = :d AND page_number = 2")
                    .param("d", documentId).query(Boolean.class).single()).isTrue();
            return null;
        });
    }
}
