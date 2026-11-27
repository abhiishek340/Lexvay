package com.lexvay.common;

import java.nio.file.Path;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for tests that need the real database. One PostgreSQL 18.7 container per test JVM
 * (DECISIONS.md D-032): Spring caches the application context across test classes, so the container
 * must outlive any single class. Testcontainers' Ryuk sidecar removes it when the JVM exits. The same
 * role script as docker compose runs inside the container, so tests connect as the application role
 * that cannot bypass row-level security, and Flyway runs as the owner.
 *
 * <p>Rate limits are set so high that no test trips them by accident; a test about the limiter overrides
 * them with its own {@code @TestPropertySource}.
 */
@TestPropertySource(properties = {
        "lexvay.ratelimit.login-per-minute=100000",
        "lexvay.ratelimit.api-per-minute=100000",
        "lexvay.ratelimit.upload-per-minute=100000"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestTenants.class, SchemaFixtures.class})
public abstract class AbstractDatabaseTest {

    protected static final String DB_NAME = "lexvay";
    protected static final String OWNER_USER = "lexvay_owner";
    protected static final String OWNER_PASSWORD = "lexvay-owner-test";
    protected static final String APP_USER = "lexvay_app";
    protected static final String APP_PASSWORD = "lexvay-app-test";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.7")
            .withEnv("LEXVAY_DB_NAME", DB_NAME)
            .withEnv("LEXVAY_DB_OWNER_USER", OWNER_USER)
            .withEnv("LEXVAY_DB_OWNER_PASSWORD", OWNER_PASSWORD)
            .withEnv("LEXVAY_DB_APP_USER", APP_USER)
            .withEnv("LEXVAY_DB_APP_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("..", "db", "initdb", "01-roles.sh"), 0755),
                    "/docker-entrypoint-initdb.d/01-roles.sh");

    static {
        POSTGRES.start();
    }

    protected static String jdbcUrl() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + DB_NAME;
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractDatabaseTest::jdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.url", AbstractDatabaseTest::jdbcUrl);
        registry.add("spring.flyway.user", () -> OWNER_USER);
        registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
        registry.add("lexvay.jobs.polling-enabled", () -> "false");
        registry.add("lexvay.llm.price.input-per-mtok", () -> "2.00");
        registry.add("lexvay.llm.price.output-per-mtok", () -> "10.00");
        registry.add("lexvay.filestore.path", () -> FILE_STORE_DIR.toString());
    }

    protected static final Path FILE_STORE_DIR = createTempDir();

    private static Path createTempDir() {
        try {
            return java.nio.file.Files.createTempDirectory("lexvay-files-");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
