package com.lexvay.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;

class MigrationsApplyCleanlyTest extends AbstractDatabaseTest {

    @Autowired Flyway flyway;
    @Autowired JdbcClient jdbc;

    @Test
    void allMigrationsAppliedAndValid() throws java.io.IOException {
        var info = flyway.info();
        assertThat(info.pending()).isEmpty();
        assertThat(info.applied()).hasSize(migrationFiles());
        for (var migration : info.applied()) {
            assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS);
        }
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    /** Every versioned migration on the classpath; version numbers may skip (V006 belongs to another work item). */
    private static int migrationFiles() throws java.io.IOException {
        return new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql").length;
    }

    @Test
    void jurisdictionsAreSeeded() {
        Long count = jdbc.sql("SELECT count(*) FROM jurisdiction").query(Long.class).single();
        assertThat(count).isEqualTo(51);
    }
}
