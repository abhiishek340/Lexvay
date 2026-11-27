package com.lexvay.document;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.lexvay.audit.AuditRepository;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.SchemaFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.job.JobRunner;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** F11 through the real upload, job and read paths, with a fake OCR engine. */
class DocumentOcrIngestTest extends AbstractDatabaseTest {

    static final String PAGE_ONE = "COUN 605 Professional Orientation and Ethical Practice. 3 credits.";
    static final String PAGE_TWO = "Week 3: Legal standards, confidentiality and duty to warn in counseling practice.";

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired AuditRepository audit;
    @Autowired JobRunner runner;
    @Autowired DocumentRepository documents;
    @Autowired SchemaFixtures schema;
    @Autowired JdbcClient jdbc;

    TestTenants.TenantWithUsers t;
    UUID programId;

    @BeforeEach
    void setUp() throws Exception {
        t = tenants.create("ocr");
        var created = mvc.post().uri("/api/v1/programs").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "P", "licenseType", "L", "catalogYear", "2026-27", "jurisdictions", List.of("CA"))))
                .exchange();
        programId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asString());
    }

    private UUID upload(String filename, byte[] pdf) throws Exception {
        var result = mvc.perform(multipart("/api/v1/programs/" + programId + "/documents")
                .file(new MockMultipartFile("file", filename, "application/pdf", pdf))
                .param("kind", "SYLLABUS").with(user(t.contributor())).with(csrf()));
        assertThat(result).hasStatus(202);
        runner.runPending();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("documentId").asString());
    }

    private JsonNode getDocument(UUID id) throws Exception {
        return json.readTree(mvc.get().uri("/api/v1/documents/" + id).with(user(t.reviewer())).exchange()
                .getResponse().getContentAsString());
    }

    @Test
    void scannedPdfIsStoredAsOcrPagesWithConfidenceAndTheAuditCountsThem() throws Exception {
        ocr.enable().page(PAGE_ONE, 93.5).page(PAGE_TWO, 71.25);
        UUID documentId = upload("scan.pdf", TestDocuments.scannedPdf(List.of(List.of("one"), List.of("two")), 72));

        JsonNode doc = getDocument(documentId);
        assertThat(doc.get("ingest").get("status").asString()).isEqualTo("DONE");
        assertThat(doc.get("pages")).hasSize(2);
        assertThat(doc.get("pages").get(0).get("textSource").asString()).isEqualTo("OCR");
        assertThat(doc.get("pages").get(0).get("ocrConfidence").asDouble()).isEqualTo(93.5);
        assertThat(doc.get("pages").get(1).get("ocrConfidence").asDouble()).isEqualTo(71.25);
        assertThat(doc.get("pages").get(1).get("charStart").asInt()).isEqualTo(PAGE_ONE.length() + 1);

        List<DocumentPage> pages = TenantContext.runAs(t.tenantId(), () -> documents.pages(documentId));
        assertThat(pages).extracting(DocumentPage::text).containsExactly(PAGE_ONE, PAGE_TWO);

        var ingested = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(documentId)).stream()
                .filter(e -> e.action().equals("DOCUMENT_INGESTED")).findFirst().orElseThrow();
        assertThat(ingested.detail().get("ocrPages")).isEqualTo(2);
        assertThat(ocr.imagesSeen()).hasSize(2);
    }

    @Test
    void textLayerPagesReportTheirSourceAndNoConfidence() throws Exception {
        UUID documentId = upload("text.pdf", TestDocuments.pdf(List.of(List.of(PAGE_ONE))));
        JsonNode page = getDocument(documentId).get("pages").get(0);
        assertThat(page.get("textSource").asString()).isEqualTo("TEXT_LAYER");
        assertThat(page.get("ocrConfidence").isNull()).isTrue();
        var ingested = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(documentId)).stream()
                .filter(e -> e.action().equals("DOCUMENT_INGESTED")).findFirst().orElseThrow();
        assertThat(ingested.detail().get("ocrPages")).isEqualTo(0);
    }

    @Test
    void scannedPdfWithoutAnEngineFailsWithTheHint() throws Exception {
        UUID documentId = upload("scan.pdf", TestDocuments.scannedPdf(List.of(List.of("one")), 72));
        JsonNode doc = getDocument(documentId);
        assertThat(doc.get("ingest").get("status").asString()).isEqualTo("FAILED");
        assertThat(doc.get("ingest").get("error").asString())
                .isEqualTo("Document has no extractable text. It looks scanned; OCR is not enabled on this server");
        assertThat(doc.get("pages")).isEmpty();
    }

    @Test
    void moreScannedPagesThanTheCapIsAPermanentFailureWithAClearMessage() throws Exception {
        ocr.enable();
        UUID documentId = upload("big.pdf", TestDocuments.pdf(Collections.nCopies(201, List.of())));
        JsonNode ingest = getDocument(documentId).get("ingest");
        assertThat(ingest.get("status").asString()).isEqualTo("FAILED");
        assertThat(ingest.get("error").asString()).contains("201 scanned pages").contains("limit is 200 pages per document");
        assertThat(ocr.imagesSeen()).isEmpty();
    }

    @Test
    void theSchemaTiesConfidenceToTheOcrSourceAndKeepsPagesImmutable() {
        UUID tenantId = t.tenantId();
        SchemaFixtures.Chain chain = schema.insertChain(tenantId, t.admin().id());
        String insert = "INSERT INTO document_page (tenant_id, document_id, page_number, text, char_start, char_end, text_source, ocr_confidence) "
                + "VALUES (:t, :d, :n, 'x', 0, 1, :source, :confidence)";

        assertThat(insertPage(insert, chain, 2, "OCR", 87.5)).isEqualTo(1);
        assertThatThrownBy(() -> insertPage(insert, chain, 3, "OCR", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPage(insert, chain, 4, "TEXT_LAYER", 90.0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPage(insert, chain, 5, "OCR", 100.5)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPage(insert, chain, 6, "HANDWRITING", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> TenantContext.runAs(tenantId, () -> jdbc.sql(
                "UPDATE document_page SET ocr_confidence = 99, text_source = 'OCR' WHERE id = :id").param("id", chain.pageId()).update()))
                .hasMessageContaining("Lexvay");
        String defaulted = TenantContext.runAs(tenantId, () -> jdbc.sql("SELECT text_source FROM document_page WHERE id = :id")
                .param("id", chain.pageId()).query(String.class).single());
        assertThat(defaulted).isEqualTo("TEXT_LAYER");
    }

    private int insertPage(String sql, SchemaFixtures.Chain chain, int number, String source, Double confidence) {
        return TenantContext.runAs(chain.tenantId(), () -> jdbc.sql(sql).param("t", chain.tenantId()).param("d", chain.documentId())
                .param("n", number).param("source", source).param("confidence", confidence, java.sql.Types.NUMERIC).update());
    }
}
