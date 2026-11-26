package com.lexvay.document;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.lexvay.common.AbstractDatabaseTest;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DocumentPageApiTest extends AbstractDatabaseTest {

    static final String IMMUTABLE = "private, max-age=31536000, immutable";

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired JobRunner runner;

    TestTenants.TenantWithUsers t;
    UUID courseDocument;
    UUID looseDocument;
    byte[] coursePdf;

    @BeforeEach
    void setUp() throws Exception {
        t = tenants.create("pages");
        UUID program = UUID.fromString(read(mvc.post().uri("/api/v1/programs").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "P", "licenseType", "L", "catalogYear", "2026-27", "jurisdictions", List.of("CA"))))
                .exchange()).get("id").asString());
        JsonNode course = read(mvc.post().uri("/api/v1/programs/" + program + "/courses").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", "COUN 605", "title", "Ethics", "required", false)))
                .exchange());
        UUID courseVersion = UUID.fromString(course.get("versions").get(0).get("id").asString());
        coursePdf = TestDocuments.pdf(List.of(List.of("Page one of the syllabus."), List.of("Page two of the syllabus.")));
        courseDocument = upload(program, "coun605.pdf", coursePdf, "SYLLABUS", courseVersion);
        looseDocument = upload(program, "handbook.pdf", TestDocuments.pdf(List.of(List.of("Handbook text."))), "HANDBOOK", null);
        runner.runPending();
    }

    private UUID upload(UUID program, String filename, byte[] bytes, String kind, UUID courseVersion) throws Exception {
        var request = multipart("/api/v1/programs/" + program + "/documents")
                .file(new MockMultipartFile("file", filename, "application/pdf", bytes)).param("kind", kind)
                .with(user(t.contributor())).with(csrf());
        if (courseVersion != null) {
            request.param("courseVersionId", courseVersion.toString());
        }
        return UUID.fromString(read(mvc.perform(request)).get("documentId").asString());
    }

    private JsonNode read(org.springframework.test.web.servlet.assertj.MvcTestResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void returnsThePageTextWithItsCourseAndDocumentContext() throws Exception {
        var result = mvc.get().uri("/api/v1/documents/" + courseDocument + "/pages/2").with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        JsonNode page = read(result);
        assertThat(page.get("documentId").asString()).isEqualTo(courseDocument.toString());
        assertThat(page.get("filename").asString()).isEqualTo("coun605.pdf");
        assertThat(page.get("kind").asString()).isEqualTo("SYLLABUS");
        assertThat(page.get("courseCode").asString()).isEqualTo("COUN 605");
        assertThat(page.get("courseRequired").asBoolean()).isFalse();
        assertThat(page.get("pageNumber").asInt()).isEqualTo(2);
        assertThat(page.get("pageCount").asInt()).isEqualTo(2);
        assertThat(page.get("text").asString()).contains("Page two of the syllabus.");
        assertThat(page.has("sha256")).as("the hash is only the ETag").isFalse();
    }

    @Test
    void aDocumentWithoutACourseHasNullCourseFields() throws Exception {
        JsonNode page = read(mvc.get().uri("/api/v1/documents/" + looseDocument + "/pages/1").with(user(t.reviewer())).exchange());
        assertThat(page.get("courseCode").isNull()).isTrue();
        assertThat(page.get("courseRequired").isNull()).isTrue();
        assertThat(page.get("pageCount").asInt()).isEqualTo(1);
    }

    @Test
    void isCachedPrivatelyAndImmutablyWithAStrongEtagOfHashAndPage() {
        var result = mvc.get().uri("/api/v1/documents/" + courseDocument + "/pages/1").with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo(IMMUTABLE);
        String etag = result.getResponse().getHeader("ETag");
        assertThat(etag).isEqualTo("\"" + DocumentService.sha256(coursePdf) + "-1\"");
        assertThat(etag).doesNotStartWith("W/");
        var otherPage = mvc.get().uri("/api/v1/documents/" + courseDocument + "/pages/2").with(user(t.reviewer())).exchange();
        assertThat(otherPage.getResponse().getHeader("ETag")).isNotEqualTo(etag);
    }

    @Test
    void ifNoneMatchWithTheEtagGets304WithoutABodyAndStillCarriesTheCachingHeaders() throws Exception {
        String etag = mvc.get().uri("/api/v1/documents/" + courseDocument + "/pages/1").with(user(t.reviewer())).exchange()
                .getResponse().getHeader("ETag");
        var revalidated = mvc.get().uri("/api/v1/documents/" + courseDocument + "/pages/1").header("If-None-Match", etag)
                .with(user(t.reviewer())).exchange();
        assertThat(revalidated).hasStatus(304);
        assertThat(revalidated.getResponse().getContentAsString()).isEmpty();
        assertThat(revalidated.getResponse().getHeader("ETag")).isEqualTo(etag);
        assertThat(revalidated.getResponse().getHeader("Cache-Control")).isEqualTo(IMMUTABLE);

        var otherPage = mvc.get().uri("/api/v1/documents/" + courseDocument + "/pages/2").header("If-None-Match", etag)
                .with(user(t.reviewer())).exchange();
        assertThat(otherPage).as("an ETag of another page does not match").hasStatus(200);
    }

    @Test
    void aMissingPageOrDocumentIs404AndIsNotCached() {
        var page = mvc.get().uri("/api/v1/documents/" + courseDocument + "/pages/3").with(user(t.reviewer())).exchange();
        assertThat(page).hasStatus(404);
        assertThat(page.getResponse().getHeader("Cache-Control")).doesNotContain("immutable");
        assertThat(mvc.get().uri("/api/v1/documents/" + courseDocument + "/pages/0").with(user(t.reviewer())).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/documents/" + UUID.randomUUID() + "/pages/1").with(user(t.reviewer())).exchange()).hasStatus(404);
    }

    @Test
    void anotherTenantGets404() {
        var other = tenants.create("other");
        assertThat(mvc.get().uri("/api/v1/documents/" + courseDocument + "/pages/1").with(user(other.admin())).exchange()).hasStatus(404);
    }

    @Test
    void unauthenticatedGets401() {
        assertThat(mvc.get().uri("/api/v1/documents/" + courseDocument + "/pages/1").exchange()).hasStatus(401);
    }
}
