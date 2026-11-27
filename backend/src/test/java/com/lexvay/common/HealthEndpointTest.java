package com.lexvay.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

class HealthEndpointTest extends AbstractDatabaseTest {

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void probesStayOnTheMainPortAndArePublic() {
        for (String probe : new String[] {"/readyz", "/livez"}) {
            var response = client().get().uri(probe).retrieve().toEntity(String.class);
            assertThat(response.getStatusCode()).as(probe).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).as(probe).contains("\"status\":\"UP\"");
        }
    }

    @Test
    void everythingElseRequiresAuthentication() {
        var response = client().get().uri("/api/v1/anything").retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
    }
}
