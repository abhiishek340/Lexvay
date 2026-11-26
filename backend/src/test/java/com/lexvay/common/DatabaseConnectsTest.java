package com.lexvay.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class DatabaseConnectsTest extends AbstractDatabaseTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Flyway flyway;

    @Test
    void serviceConnectsAsApplicationRoleThatCannotBypassRowLevelSecurity() {
        var row = jdbc.sql("SELECT current_user AS who, rolbypassrls AS bypass, rolsuper AS super "
                        + "FROM pg_roles WHERE rolname = current_user")
                .query().singleRow();
        assertThat(row.get("who")).isEqualTo(APP_USER);
        assertThat(row.get("bypass")).isEqualTo(false);
        assertThat(row.get("super")).isEqualTo(false);
    }

    @Test
    void databaseIsPostgres18() {
        String version = jdbc.sql("SELECT version()").query(String.class).single();
        assertThat(version).startsWith("PostgreSQL 18.");
    }

    @Test
    void flywayRunsAsOwner() throws Exception {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT current_user")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo(OWNER_USER);
        }
        assertThat(flyway.info().applied()).isNotEmpty();
    }
}
