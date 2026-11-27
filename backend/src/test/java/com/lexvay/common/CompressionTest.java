package com.lexvay.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

/** JSON over 1 kB is gzip-compressed for clients that accept it; small responses are sent as they are. */
class CompressionTest extends AbstractDatabaseTest {

    @LocalServerPort int port;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;

    @Test
    void jsonOverOneKilobyteIsGzippedAndSmallJsonIsNot() throws Exception {
        var t = tenants.create("gzip");
        TestHttpClient api = new TestHttpClient(port);
        api.login(t.admin().email(), TestTenants.PASSWORD);
        for (int i = 0; i < 12; i++) {
            var created = api.postJson("/api/v1/programs", json.writeValueAsString(Map.of("name", "Compression program number " + i,
                    "licenseType", "LPC", "catalogYear", "2026-27", "jurisdictions", List.of("CA", "TX", "UT"))));
            assertThat(created.status()).isEqualTo(201);
        }

        var large = api.get("/api/v1/programs");
        assertThat(large.status()).isEqualTo(200);
        assertThat(large.body().length()).isGreaterThan(1024);
        assertThat(large.contentEncoding()).isEqualTo("gzip");
        assertThat(large.wireBytes()).isLessThan(large.body().length());
        assertThat(json.readTree(large.body())).hasSize(12);

        var small = api.get("/api/v1/auth/me");
        assertThat(small.body().length()).isLessThan(1024);
        assertThat(small.contentEncoding()).isNull();
    }
}
