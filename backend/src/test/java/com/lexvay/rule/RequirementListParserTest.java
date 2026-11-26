package com.lexvay.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RequirementListParserTest {

    RequirementListParser parser = new RequirementListParser(JsonMapper.builder().build());

    private static String item(String ref, String type, String field, String op, String value) {
        return "{\"ref\":\"" + ref + "\",\"title\":\"T\",\"text\":\"Some text\",\"type\":\"" + type + "\","
                + "\"numeric_field\":" + (field == null ? "null" : "\"" + field + "\"") + ","
                + "\"numeric_operator\":" + (op == null ? "null" : "\"" + op + "\"") + ","
                + "\"numeric_value\":" + (value == null ? "null" : value) + "}";
    }

    @Test
    void parsesNumericAndSubjectRequirements() {
        var drafts = parser.parse("{\"requirements\":[" + item("CA-1", "SUPERVISED_HOURS", "INTERNSHIP_HOURS", ">=", "600")
                + "," + item("CA-2", "SUBJECT", null, null, null) + "]}");
        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).numericField()).isEqualTo(NumericField.INTERNSHIP_HOURS);
        assertThat(drafts.get(0).numericValue()).isEqualByComparingTo(new BigDecimal("600"));
        assertThat(drafts.get(1).numericField()).isNull();
    }

    @Test
    void duplicateRefsAreRejected() {
        assertThatThrownBy(() -> parser.parse("{\"requirements\":[" + item("CA-1", "SUBJECT", null, null, null) + ","
                + item("CA-1", "SUBJECT", null, null, null) + "]}"))
                .isInstanceOf(InvalidRequirementListException.class).hasMessageContaining("duplicated");
    }

    @Test
    void numericTypeWithoutFieldsIsRejected() {
        assertThatThrownBy(() -> parser.parse("{\"requirements\":[" + item("CA-1", "NUMERIC", null, null, null) + "]}"))
                .hasMessageContaining("needs numeric_field");
    }

    @Test
    void subjectTypeWithNumericFieldsIsRejected() {
        assertThatThrownBy(() -> parser.parse("{\"requirements\":[" + item("CA-1", "SUBJECT", "TOTAL_CREDITS", ">=", "60") + "]}"))
                .hasMessageContaining("must not carry numeric fields");
    }

    @Test
    void emptyListAndNonPositiveValuesAreRejected() {
        assertThatThrownBy(() -> parser.parse("{\"requirements\":[]}")).hasMessageContaining("at least one");
        assertThatThrownBy(() -> parser.parse("{\"requirements\":[" + item("CA-1", "NUMERIC", "TOTAL_CREDITS", ">=", "0") + "]}"))
                .hasMessageContaining("positive number");
        assertThatThrownBy(() -> parser.parse("{\"requirements\":[" + item("CA-1", "NUMERIC", "TOTAL_CREDITS", "~", "60") + "]}"))
                .hasMessageContaining("numeric_operator");
    }

    @Test
    void schemaErrorsSurfaceAsInvalidList() {
        assertThatThrownBy(() -> parser.parse("not json")).isInstanceOf(InvalidRequirementListException.class)
                .hasMessageContaining("not valid JSON");
    }
}
