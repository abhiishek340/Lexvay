package com.lexvay.search;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import com.lexvay.job.JobRunner;
import com.lexvay.seed.SeedManifest;
import com.lexvay.auth.LexvayUser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * A fresh tenant holding the real seed documents of the demo university (its courses and PDFs from seed/), ingested
 * through the API exactly as a contributor would. It does not use SeedLoader, which loads the shared demo tenants once
 * per database and is asserted on by SeedLoaderTest; this class needs no model and cannot disturb that test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractSearchTest extends AbstractDatabaseTest {

    @Autowired protected MockMvcTester mvc;
    @Autowired protected TestTenants tenants;
    @Autowired protected ObjectMapper json;
    @Autowired protected JobRunner runner;

    protected TestTenants.TenantWithUsers tenant;
    protected UUID programId;
    protected final Map<String, UUID> courseVersionByCode = new HashMap<>();

    @BeforeAll
    void loadSeedDocuments() throws Exception {
        SeedManifest.Tenant northfield = YAMLMapper.builder().build()
                .readValue(Files.readString(Path.of("..", "seed", "manifest.yml")), SeedManifest.class).tenants().get(0);
        tenant = tenants.create("search");
        var program = mvc.post().uri("/api/v1/programs").with(user(tenant.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Search fixture", "licenseType", "CLINICAL_MENTAL_HEALTH_COUNSELING",
                        "catalogYear", "2026-27", "jurisdictions", List.of("CA"))))
                .exchange();
        programId = UUID.fromString(json.readTree(program.getResponse().getContentAsString()).get("id").asString());
        for (SeedManifest.Course c : northfield.courses()) {
            var course = mvc.post().uri("/api/v1/programs/" + programId + "/courses").with(user(tenant.contributor())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("code", c.code(), "title", c.title(), "required", c.required())))
                    .exchange();
            UUID versionId = UUID.fromString(json.readTree(course.getResponse().getContentAsString())
                    .get("versions").get(0).get("id").asString());
            courseVersionByCode.put(c.code(), versionId);
            if (c.syllabus() != null) {
                upload(c.syllabus(), "SYLLABUS", versionId);
            }
        }
        for (SeedManifest.Document d : northfield.documents()) {
            upload(d.file(), d.kind(), null);
        }
        runner.runPending();
    }

    private void upload(String file, String kind, UUID courseVersionId) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("..", "seed", "pdf", file));
        var request = multipart("/api/v1/programs/" + programId + "/documents")
                .file(new MockMultipartFile("file", file, "application/pdf", bytes))
                .param("kind", kind)
                .with(user(tenant.contributor())).with(csrf());
        if (courseVersionId != null) {
            request.param("courseVersionId", courseVersionId.toString());
        }
        assertThat(mvc.perform(request)).hasStatus(202);
    }

    /** The search URL with the query encoded as a browser would; {@code extraParams} is appended verbatim. */
    protected static URI searchUri(UUID program, String query, String extraParams) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create("/api/v1/programs/" + program + "/search?q=" + encoded + extraParams);
    }

    protected MvcTestResult searchAs(LexvayUser as, URI uri) {
        return mvc.get().uri(uri).with(user(as)).exchange();
    }

    protected JsonNode search(UUID program, String query, String extraParams) throws Exception {
        var result = searchAs(tenant.reviewer(), searchUri(program, query, extraParams));
        assertThat(result).hasStatus(200);
        return json.readTree(result.getResponse().getContentAsString());
    }
}
