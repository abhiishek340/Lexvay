package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 8: there is no student entity anywhere in the schema. */
class Rule8_NoStudentEntityTest extends AbstractDatabaseTest {

    @Autowired JdbcClient jdbc;

    @Test
    void noTableOrColumnMentionsStudents() {
        List<String> offenders = jdbc.sql("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND (table_name ILIKE '%student%' OR column_name ILIKE '%student%'
                   OR table_name ILIKE '%enroll%' OR column_name ILIKE '%enroll%')
                """).query(String.class).list();
        assertThat(offenders).isEmpty();
    }
}
