package com.lexvay.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

/** Metrics and the full health report live on their own port; the main port exposes none of them. */
class ManagementPortTest extends AbstractDatabaseTest {

    @LocalServerPort int port;
    @LocalManagementPort int managementPort;

    private int status(int onPort, String path) {
        return RestClient.builder().baseUrl("http://localhost:" + onPort)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build().get().uri(path).retrieve().toBodilessEntity().getStatusCode().value();
    }

    private String body(int onPort, String path) {
        return RestClient.create("http://localhost:" + onPort).get().uri(path).retrieve().body(String.class);
    }

    @Test
    void theManagementPortIsASeparatePort() {
        assertThat(managementPort).isNotEqualTo(port);
    }

    @Test
    void prometheusAndHealthAreReadableOnTheManagementPortWithoutSignIn() {
        assertThat(body(managementPort, "/actuator/prometheus")).contains("lexvay_job_queue_depth", "jvm_memory_used_bytes");
        assertThat(body(managementPort, "/actuator/health")).contains("\"status\":\"UP\"");
    }

    @Test
    void nothingElseIsReadableOnTheManagementPort() {
        assertThat(status(managementPort, "/actuator/env")).isIn(401, 404);
        assertThat(status(managementPort, "/actuator/beans")).isIn(401, 404);
        assertThat(status(managementPort, "/api/v1/programs")).isEqualTo(401);
    }

    @Test
    void theMainPortDoesNotServeTheActuator() {
        assertThat(status(port, "/actuator/prometheus")).isEqualTo(401);
        assertThat(status(port, "/actuator/health")).isEqualTo(404);
        assertThat(status(port, "/actuator/env")).isEqualTo(401);
    }
}
