package com.lexvay.evidencepackage;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import com.lexvay.document.DocumentService;
import com.lexvay.job.JobRunner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The report and package requests of a signed determination, and a way to open what comes back. */
final class PackageFixtures {

    private final MockMvcTester mvc;
    private final ObjectMapper json;
    private final JobRunner runner;

    PackageFixtures(MockMvcTester mvc, ObjectMapper json, JobRunner runner) {
        this.mvc = mvc;
        this.json = json;
        this.runner = runner;
    }

    /** Requests the report of a determination as {@code as} and runs the queue until it is ready; returns the report id. */
    UUID readyReport(LexvayUser as, UUID determinationId) {
        UUID reportId = id(post(as, "/api/v1/determinations/" + determinationId + "/report"), "reportId");
        runner.runPending();
        return reportId;
    }

    MvcTestResult requestPackage(LexvayUser as, UUID determinationId) {
        return post(as, "/api/v1/determinations/" + determinationId + "/package");
    }

    /** Requests the package and returns its id, having checked the request was accepted. */
    UUID acceptedPackage(LexvayUser as, UUID determinationId) {
        MvcTestResult requested = requestPackage(as, determinationId);
        assertThat(requested).hasStatus(202);
        return id(requested, "packageId");
    }

    JsonNode status(LexvayUser as, UUID packageId) {
        MvcTestResult result = mvc.get().uri("/api/v1/packages/" + packageId).with(user(as)).exchange();
        assertThat(result).hasStatus(200);
        return read(result);
    }

    MvcTestResult file(LexvayUser as, UUID packageId) {
        return mvc.get().uri("/api/v1/packages/" + packageId + "/file").with(user(as)).exchange();
    }

    /** The entries of a ZIP in their order, with their bytes. */
    static Map<String, byte[]> unzip(byte[] zip) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    /** The manifest lists every other entry with its size and SHA-256, each as the entry's own bytes recompute; returns it. */
    JsonNode checkedManifest(Map<String, byte[]> entries) {
        JsonNode manifest = json.readTree(entries.get("manifest.json"));
        assertThat(manifest.get("hashAlgorithm").asString()).isEqualTo("SHA-256");
        JsonNode listed = manifest.get("entries");
        assertThat(listed).hasSize(entries.size() - 1);
        for (JsonNode entry : listed) {
            String path = entry.get("path").asString();
            assertThat(path).isNotEqualTo("manifest.json");
            assertThat(entries).containsKey(path);
            assertThat(entry.get("size").asLong()).as(path).isEqualTo(entries.get(path).length);
            assertThat(entry.get("sha256").asString()).as(path).isEqualTo(DocumentService.sha256(entries.get(path)));
        }
        return manifest;
    }

    JsonNode read(MvcTestResult result) {
        return json.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    private UUID id(MvcTestResult result, String field) {
        return UUID.fromString(read(result).get(field).asString());
    }

    private MvcTestResult post(LexvayUser as, String uri) {
        return mvc.post().uri(uri).with(user(as)).with(csrf()).exchange();
    }
}
