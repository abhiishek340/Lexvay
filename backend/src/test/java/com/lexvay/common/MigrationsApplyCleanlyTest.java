package com.lexvay.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class MigrationsApplyCleanlyTest extends AbstractDatabaseTest {

    @Autowired Flyway flyway;
    @Autowired JdbcClient jdbc;

    @Test
    void allMigrationsAppliedAndValid() {
        var info = flyway.info();
        assertThat(info.pending()).isEmpty();
        assertThat(info.applied()).hasSize(5);
        for (var migration : info.applied()) {
            assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS);
        }
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void jurisdictionsAreSeeded() {
        Long count = jdbc.sql("SELECT count(*) FROM jurisdiction").query(Long.class).single();
        assertThat(count).isEqualTo(51);
    }
}
