package com.lexvay.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

/** The operational settings as {@code application.yml} resolves them, without starting an application. */
class OperationalConfigTest {

    private static ConfigurableEnvironment environment(String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    @Test
    void productionLogsAreEcsJsonAndLocalLogsAreNot() {
        assertThat(environment("prod").getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(environment("prod").getProperty("logging.structured.ecs.service.name")).isEqualTo("lexvay");
        assertThat(environment().getProperty("logging.structured.format.console")).isNull();
        assertThat(environment().getProperty("logging.pattern.correlation")).contains("requestId").contains("jobId");
    }

    @Test
    void shutdownIsGracefulAndTheSchedulerWaitsForTheRunningJob() {
        var environment = environment();
        assertThat(environment.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(environment.getProperty("spring.task.scheduling.shutdown.await-termination")).isEqualTo("true");
        assertThat(environment.getProperty("spring.task.scheduling.shutdown.await-termination-period"))
                .isEqualTo(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("30s");
    }

    @Test
    void managementRunsOnItsOwnPortExposingOnlyHealthAndPrometheus() {
        var environment = environment();
        assertThat(environment.getProperty("management.server.port")).isEqualTo("8090");
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,prometheus");
        assertThat(environment.getProperty("management.endpoint.health.probes.add-additional-paths")).isEqualTo("true");
        assertThat(environment("seed").getProperty("management.server.port")).isEqualTo("-1");
    }
}
