package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * The multipart size limit ({@code LEXVAY_UPLOAD_MAX_MB}) is enforced by the servlet container, which MockMvc
 * does not have, so this test uploads over a real connection. The limit is set to 1 MB to keep it fast.
 */
@TestPropertySource(properties = "LEXVAY_UPLOAD_MAX_MB=1")
class UploadSizeLimitTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @LocalServerPort int port;

    RestClient client;
    String cookies;
    String programId;
    UUID tenantId;

    @BeforeEach
    void signInAndCreateAProgram() throws Exception {
        var t = tenants.create("sizelimit");
        tenantId = t.tenantId();
        client = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
        var login = client.post().uri("/api/v1/auth/login")
                .header("Cookie", "XSRF-TOKEN=abc").header("X-XSRF-TOKEN", "abc")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=" + t.contributor().email() + "&password=" + TestTenants.PASSWORD)
                .retrieve().toEntity(String.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        String session = login.getHeaders().get("Set-Cookie").stream().filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        cookies = session + "; XSRF-TOKEN=abc";
        var created = client.post().uri("/api/v1/programs").header("Cookie", cookies).header("X-XSRF-TOKEN", "abc")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(Map.of("name", "P", "licenseType", "L", "catalogYear", "2026-27", "jurisdictions", List.of("CA"))))
                .retrieve().toEntity(String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        programId = json.readTree(created.getBody()).get("id").asString();
    }

    private org.springframework.http.ResponseEntity<String> upload(byte[] bytes) {
        var parts = file("notes.txt", bytes);
        parts.add("kind", "HANDBOOK");
        return post("/api/v1/programs/" + programId + "/documents", parts);
    }

    private static LinkedMultiValueMap<String, Object> file(String filename, byte[] bytes) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return parts;
    }

    private org.springframework.http.ResponseEntity<String> post(String uri, LinkedMultiValueMap<String, Object> parts) {
        return client.post().uri(uri)
                .header("Cookie", cookies).header("X-XSRF-TOKEN", "abc")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(parts)
                .retrieve().toEntity(String.class);
    }

    @Test
    void aFileOverTheLimitIsRefusedWith413AndAProblemDetail() throws Exception {
        var response = upload(new byte[1024 * 1024 + 512 * 1024]);
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        var problem = json.readTree(response.getBody());
        assertThat(problem.get("status").asInt()).isEqualTo(413);
        assertThat(problem.get("detail").asString()).isNotBlank();
    }

    /** The report check (F14) has no limit of its own: the same multipart limit refuses the file before it is read. */
    @Test
    void aReportCheckOverTheLimitIsRefusedWith413AndIsNotAudited() throws Exception {
        byte[] pdf = new byte[1024 * 1024 + 512 * 1024];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, pdf, 0, 5);
        var response = post("/api/v1/reports/check", file("report.pdf", pdf));
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(TenantContext.runAs(tenantId, () -> jdbc.sql("SELECT count(*) FROM audit_log WHERE action = 'REPORT_CHECKED'")
                .query(Long.class).single())).isZero();
    }

    @Test
    void aFileUnderTheLimitIsAccepted() {
        byte[] text = new byte[900 * 1024];
        java.util.Arrays.fill(text, (byte) 'a');
        assertThat(upload(text).getStatusCode().value()).isEqualTo(202);
    }
}
