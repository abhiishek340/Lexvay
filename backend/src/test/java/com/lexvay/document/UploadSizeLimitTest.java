package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
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
    @LocalServerPort int port;

    RestClient client;
    String cookies;
    String programId;

    @BeforeEach
    void signInAndCreateAProgram() throws Exception {
        var t = tenants.create("sizelimit");
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
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "notes.txt";
            }
        });
        parts.add("kind", "HANDBOOK");
        return client.post().uri("/api/v1/programs/" + programId + "/documents")
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

    @Test
    void aFileUnderTheLimitIsAccepted() {
        byte[] text = new byte[900 * 1024];
        java.util.Arrays.fill(text, (byte) 'a');
        assertThat(upload(text).getStatusCode().value()).isEqualTo(202);
    }
}
