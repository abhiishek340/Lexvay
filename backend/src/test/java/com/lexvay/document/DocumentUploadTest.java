package com.lexvay.document;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.lexvay.audit.AuditRepository;
import com.lexvay.auth.LexvayUser;
import com.lexvay.common.AbstractDatabaseTest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DocumentUploadTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired AuditRepository audit;
    @Autowired JobRunner runner;
    @Autowired DocumentRepository documents;
    @Autowired FileStore files;

    TestTenants.TenantWithUsers t;
    UUID programId;
    UUID courseVersionId;

    @BeforeEach
    void setUp() throws Exception {
        t = tenants.create("docs");
        var created = mvc.post().uri("/api/v1/programs").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "P", "licenseType", "L", "catalogYear", "2026-27", "jurisdictions", List.of("CA"))))
                .exchange();
        programId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asString());
        var course = mvc.post().uri("/api/v1/programs/" + programId + "/courses").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", "COUN 605", "title", "Ethics", "required", true)))
                .exchange();
        courseVersionId = UUID.fromString(json.readTree(course.getResponse().getContentAsString())
                .get("versions").get(0).get("id").asString());
    }

    private MvcTestResult upload(LexvayUser as, UUID program, String filename, String contentType, byte[] bytes,
                                 String kind, UUID courseVersion) {
        var request = multipart("/api/v1/programs/" + program + "/documents")
                .file(new MockMultipartFile("file", filename, contentType, bytes))
                .param("kind", kind)
                .with(user(as)).with(csrf());
        if (courseVersion != null) {
            request.param("courseVersionId", courseVersion.toString());
        }
        return mvc.perform(request);
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getDocument(UUID id) throws Exception {
        return body(mvc.get().uri("/api/v1/documents/" + id).with(user(t.reviewer())).exchange());
    }

    @Test
    void pdfUploadIsIngestedWithOffsetsAndAudited() throws Exception {
        byte[] pdf = TestDocuments.pdf(List.of(
                List.of("Fictional. Prototype data.", "COUN 605 Professional Orientation and Ethics"),
                List.of("Week 3: Legal standards and confidentiality")));
        var result = upload(t.contributor(), programId, "coun605.pdf", "application/pdf", pdf, "SYLLABUS", courseVersionId);
        assertThat(result).hasStatus(202);
        UUID documentId = UUID.fromString(body(result).get("documentId").asString());
        UUID jobId = UUID.fromString(body(result).get("jobId").asString());

        var before = getDocument(documentId);
        assertThat(before.get("pages")).isEmpty();
        assertThat(before.get("ingest").get("status").asString()).isEqualTo("QUEUED");
        assertThat(before.get("ingest").get("jobId").asString()).isEqualTo(jobId.toString());

        runner.runPending();

        var after = getDocument(documentId);
        assertThat(after.get("ingest").get("status").asString()).isEqualTo("DONE");
        assertThat(after.get("sha256").asString()).isEqualTo(DocumentService.sha256(pdf));
        assertThat(after.get("courseVersionId").asString()).isEqualTo(courseVersionId.toString());
        assertThat(after.get("pages")).hasSize(2);
        assertThat(after.get("pages").get(0).get("charStart").asInt()).isZero();
        int firstEnd = after.get("pages").get(0).get("charEnd").asInt();
        assertThat(after.get("pages").get(1).get("charStart").asInt()).isEqualTo(firstEnd + 1);

        List<DocumentPage> pages = TenantContext.runAs(t.tenantId(), () -> documents.pages(documentId));
        assertThat(pages.get(0).text()).contains("COUN 605 Professional Orientation and Ethics");
        assertThat(pages.get(1).text()).contains("Legal standards and confidentiality");
        assertThat(files.exists(t.tenantId() + "/" + documentId)).isTrue();

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(documentId));
        assertThat(entries).extracting(e -> e.action()).containsExactlyInAnyOrder("DOCUMENT_UPLOADED", "DOCUMENT_INGESTED");
        var ingested = entries.stream().filter(e -> e.action().equals("DOCUMENT_INGESTED")).findFirst().orElseThrow();
        assertThat(ingested.userId()).isNull();
        assertThat(ingested.detail().get("jobId")).isEqualTo(jobId.toString());
        var uploaded = entries.stream().filter(e -> e.action().equals("DOCUMENT_UPLOADED")).findFirst().orElseThrow();
        assertThat(uploaded.userId()).isEqualTo(t.contributor().id());
    }

    @Test
    void docxAndTxtAreIngestedAsOnePage() throws Exception {
        byte[] docx = TestDocuments.docx(List.of("Clinical Handbook", "Practicum: 100 hours. Internship: 600 hours."));
        var docxResult = upload(t.admin(), programId, "handbook.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx, "HANDBOOK", null);
        assertThat(docxResult).hasStatus(202);
        var txtResult = upload(t.admin(), programId, "plan.txt", "text/plain",
                TestDocuments.txt("Degree plan\nTotal credits: 60\n"), "DEGREE_PLAN", null);
        assertThat(txtResult).hasStatus(202);
        runner.runPending();

        var docxDoc = getDocument(UUID.fromString(body(docxResult).get("documentId").asString()));
        assertThat(docxDoc.get("pages")).hasSize(1);
        assertThat(docxDoc.get("ingest").get("status").asString()).isEqualTo("DONE");
        var txtDoc = getDocument(UUID.fromString(body(txtResult).get("documentId").asString()));
        assertThat(txtDoc.get("pages")).hasSize(1);
        assertThat(txtDoc.get("pages").get(0).get("length").asInt()).isEqualTo("Degree plan\nTotal credits: 60".length());
    }

    @Test
    void duplicateUploadIsDetectedByHash() throws Exception {
        byte[] txt = TestDocuments.txt("same content");
        var first = upload(t.admin(), programId, "a.txt", "text/plain", txt, "HANDBOOK", null);
        assertThat(first).hasStatus(202);
        var second = upload(t.admin(), programId, "renamed.txt", "text/plain", txt, "HANDBOOK", null);
        assertThat(second).hasStatus(409);
        var problem = body(second);
        assertThat(problem.get("existingDocumentId").asString()).isEqualTo(body(first).get("documentId").asString());
        assertThat(second.getResponse().getContentType()).contains("application/problem+json");
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        var result = upload(t.admin(), programId, "empty.txt", "text/plain", new byte[0], "HANDBOOK", null);
        assertThat(result).hasStatus(400);
        assertThat(body(result).get("detail").asString()).contains("empty");
    }

    @Test
    void corruptPdfFailsIngestWithClearError() throws Exception {
        var result = upload(t.admin(), programId, "broken.pdf", "application/pdf", TestDocuments.corruptPdf(), "SYLLABUS", null);
        assertThat(result).hasStatus(202);
        runner.runPending();
        var doc = getDocument(UUID.fromString(body(result).get("documentId").asString()));
        assertThat(doc.get("ingest").get("status").asString()).isEqualTo("FAILED");
        assertThat(doc.get("ingest").get("error").asString()).contains("could not be parsed");
        assertThat(doc.get("pages")).isEmpty();
    }

    @Test
    void pdfWithoutTextLayerFailsIngestAsEmpty() throws Exception {
        byte[] blank = TestDocuments.pdf(List.of(List.of()));
        var result = upload(t.admin(), programId, "scan.pdf", "application/pdf", blank, "SYLLABUS", null);
        assertThat(result).hasStatus(202);
        runner.runPending();
        var doc = getDocument(UUID.fromString(body(result).get("documentId").asString()));
        assertThat(doc.get("ingest").get("status").asString()).isEqualTo("FAILED");
        assertThat(doc.get("ingest").get("error").asString()).isEqualTo("Document has no extractable text. It looks scanned; OCR is not enabled on this server");
    }

    @Test
    void unsupportedTypesAndRuleTextAreRejected() throws Exception {
        assertThat(upload(t.admin(), programId, "x.exe", "application/octet-stream", new byte[] {1, 2}, "SYLLABUS", null)).hasStatus(400);
        assertThat(upload(t.admin(), programId, "fake.pdf", "application/pdf", TestDocuments.txt("text"), "SYLLABUS", null)).hasStatus(400);
        var ruleText = upload(t.admin(), programId, "rule.txt", "text/plain", TestDocuments.txt("rule"), "RULE_TEXT", null);
        assertThat(ruleText).hasStatus(400);
        assertThat(body(ruleText).get("detail").asString()).contains("rule versions");
    }

    @Test
    void courseVersionMustBelongToTheProgram() throws Exception {
        var otherProgram = mvc.post().uri("/api/v1/programs").with(user(t.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Q", "licenseType", "L", "catalogYear", "2026-27", "jurisdictions", List.of("TX"))))
                .exchange();
        UUID otherId = UUID.fromString(body(otherProgram).get("id").asString());
        var result = upload(t.admin(), otherId, "s.txt", "text/plain", TestDocuments.txt("syllabus"), "SYLLABUS", courseVersionId);
        assertThat(result).hasStatus(400);
        assertThat(body(result).get("detail").asString()).contains("different program");
    }

    @Test
    void rolesAndTenancyAreEnforced() throws Exception {
        assertThat(upload(t.reviewer(), programId, "s.txt", "text/plain", TestDocuments.txt("x"), "SYLLABUS", null)).hasStatus(403);
        var other = tenants.create("other");
        assertThat(upload(other.admin(), programId, "s.txt", "text/plain", TestDocuments.txt("x"), "SYLLABUS", null)).hasStatus(404);
        var uploaded = upload(t.admin(), programId, "s.txt", "text/plain", TestDocuments.txt("x"), "SYLLABUS", null);
        UUID documentId = UUID.fromString(body(uploaded).get("documentId").asString());
        assertThat(mvc.get().uri("/api/v1/documents/" + documentId).with(user(other.admin())).exchange()).hasStatus(404);
    }
}
