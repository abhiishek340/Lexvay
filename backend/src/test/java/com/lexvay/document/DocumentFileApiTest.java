package com.lexvay.document;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.auth.LexvayUser;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.ObjectMapper;

/** {@code GET /documents/{id}/file}: the original bytes, inline, cached like the pages (F1, D-221). */
class DocumentFileApiTest extends AbstractDatabaseTest {

    static final String IMMUTABLE = "private, max-age=31536000, immutable";

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired JobRunner runner;
    @Autowired LlmClient llm;

    TestTenants.TenantWithUsers t;
    AssessmentFixtures fx;
    UUID program;
    byte[] pdf;
    UUID pdfDocument;

    @BeforeEach
    void setUp() {
        t = tenants.create("files");
        fx = new AssessmentFixtures(mvc, json, runner, (FakeLlmClient) llm);
        program = fx.program(t, List.of("CA"));
        pdf = TestDocuments.pdf(List.of(List.of("Page one of the syllabus."), List.of("Page two of the syllabus.")));
        pdfDocument = uploadPdf("coun605.pdf", pdf);
    }

    private UUID uploadPdf(String filename, byte[] bytes) {
        var result = mvc.perform(multipart("/api/v1/programs/" + program + "/documents")
                .file(new MockMultipartFile("file", filename, "application/pdf", bytes)).param("kind", "SYLLABUS")
                .with(user(t.contributor())).with(csrf()));
        assertThat(result).hasStatus(202);
        return UUID.fromString(fx.read(result).get("documentId").asString());
    }

    private String uri(UUID document) {
        return "/api/v1/documents/" + document + "/file";
    }

    @Test
    void servesTheOriginalBytesInlineAsPdfCachedPrivatelyAndImmutablyWithTheHashAsEtag() throws Exception {
        var result = mvc.get().uri(uri(pdfDocument)).with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(pdf);
        assertThat(result.getResponse().getHeader("Content-Disposition")).startsWith("inline;").contains("filename=\"coun605.pdf\"");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo(IMMUTABLE);
        assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"" + DocumentService.sha256(pdf) + "\"");
    }

    @Test
    void aFileNameOutsideAsciiIsSentRfc5987Encoded() {
        UUID document = uploadPdf("Lehrplan Übung 605.pdf", TestDocuments.pdf(List.of(List.of("Fictional syllabus."))));
        String disposition = mvc.get().uri(uri(document)).with(user(t.reviewer())).exchange().getResponse().getHeader("Content-Disposition");
        assertThat(disposition).startsWith("inline;").contains("filename*=UTF-8''Lehrplan%20%C3%9Cbung%20605.pdf");
        assertThat(disposition).doesNotContain("Ü");
    }

    @Test
    void aTextDocumentIsServedAsUtf8PlainText() throws Exception {
        UUID document = fx.document(t, program, "plan.txt", "DEGREE_PLAN", null, "Fictional. Total credits: 60. Café");
        var result = mvc.get().uri(uri(document)).with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        assertThat(result.getResponse().getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("Fictional. Total credits: 60. Café");
    }

    @Test
    void aDocxDocumentIsServedWithTheWordMediaType() {
        var result = mvc.perform(multipart("/api/v1/programs/" + program + "/documents")
                .file(new MockMultipartFile("file", "handbook.docx", "application/octet-stream",
                        TestDocuments.docx(List.of("Fictional handbook."))))
                .param("kind", "HANDBOOK").with(user(t.contributor())).with(csrf()));
        UUID document = UUID.fromString(fx.read(result).get("documentId").asString());
        assertThat(mvc.get().uri(uri(document)).with(user(t.reviewer())).exchange().getResponse().getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void ifNoneMatchWithTheEtagGets304WithoutABodyAndStillCarriesTheCachingHeaders() throws Exception {
        String etag = mvc.get().uri(uri(pdfDocument)).with(user(t.reviewer())).exchange().getResponse().getHeader("ETag");
        var revalidated = mvc.get().uri(uri(pdfDocument)).header("If-None-Match", etag).with(user(t.reviewer())).exchange();
        assertThat(revalidated).hasStatus(304);
        assertThat(revalidated.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(revalidated.getResponse().getHeaders("ETag")).containsExactly(etag);
        assertThat(revalidated.getResponse().getHeader("Cache-Control")).isEqualTo(IMMUTABLE);

        var stale = mvc.get().uri(uri(pdfDocument)).header("If-None-Match", "\"" + "0".repeat(64) + "\"")
                .with(user(t.reviewer())).exchange();
        assertThat(stale).as("another hash does not match").hasStatus(200);
    }

    @Test
    void everyRoleMayReadTheFile() {
        for (LexvayUser role : List.of(t.contributor(), t.reviewer(), t.signer(), t.admin())) {
            assertThat(mvc.get().uri(uri(pdfDocument)).with(user(role)).exchange()).as(role.role().name()).hasStatus(200);
        }
    }

    @Test
    void anotherTenantAndAnUnknownIdGet404ThatIsNotCached() {
        var other = tenants.create("files-other");
        var foreign = mvc.get().uri(uri(pdfDocument)).with(user(other.admin())).exchange();
        assertThat(foreign).hasStatus(404);
        assertThat(foreign.getResponse().getHeader("Cache-Control")).doesNotContain("immutable");
        assertThat(foreign.getResponse().getHeader("Content-Disposition")).isNull();
        assertThat(mvc.get().uri(uri(UUID.randomUUID())).with(user(t.reviewer())).exchange()).hasStatus(404);
    }

    @Test
    void unauthenticatedGets401() {
        assertThat(mvc.get().uri(uri(pdfDocument)).exchange()).hasStatus(401);
    }
}
