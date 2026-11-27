package com.lexvay.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RequirementListParserTest {

    JsonMapper json = JsonMapper.builder().build();
    RequirementListParser parser = new RequirementListParser(json);

    private static String item(String ref, String type, String field, String op, String value) {
        return item(ref, type, field, op, value, "\"search_terms\":[]");
    }

    /** {@code searchTerms} is the member's JSON text, or null to leave it out like a reviewer's PUT body may. */
    private static String item(String ref, String type, String field, String op, String value, String searchTerms) {
        return "{\"ref\":\"" + ref + "\",\"title\":\"T\",\"text\":\"Some text\",\"type\":\"" + type + "\","
                + "\"numeric_field\":" + (field == null ? "null" : "\"" + field + "\"") + ","
                + "\"numeric_operator\":" + (op == null ? "null" : "\"" + op + "\"") + ","
                + "\"numeric_value\":" + (value == null ? "null" : value) + (searchTerms == null ? "" : "," + searchTerms) + "}";
    }

    private static String subject(String searchTerms) {
        return "{\"requirements\":[" + item("CA-1", "SUBJECT", null, null, null, searchTerms) + "]}";
    }

    private static String terms(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "\"term" + (char) ('a' + i) + "\"").collect(Collectors.joining(","));
    }

    @Test
    void searchTermsAreNormalisedAgainstTheTitleAndTextOfTheirRequirement() {
        // The item's title is "T" and its text "Some text".
        var drafts = parser.parse(subject("\"search_terms\":[\" Psychopathology \",\"psychopathology\",\"text\",\"Some Text\",\"\",\"DSM  criteria\"]"));

        assertThat(drafts.get(0).searchTerms()).containsExactly("psychopathology", "dsm criteria");
    }

    @Test
    void aTermWithADigitOrAStateNameIsDropped() {
        var drafts = parser.parse(subject("\"search_terms\":[\"dsm 5\",\"utah board\",\"New York\",\"practicum\"]"));

        assertThat(drafts.get(0).searchTerms()).containsExactly("practicum");
    }

    @Test
    void absentOrNullSearchTermsMeanNoTerms() {
        assertThat(parser.parse(json.readTree(subject(null))).get(0).searchTerms()).isEmpty();
        assertThat(parser.parse(json.readTree(subject("\"search_terms\":null"))).get(0).searchTerms()).isEmpty();
        assertThat(parser.parse(subject("\"search_terms\":[]")).get(0).searchTerms()).isEmpty();
    }

    @Test
    void twelveTermsAreKeptAndThirteenAreRejectedNamingTheRow() {
        assertThat(parser.parse(json.readTree(subject("\"search_terms\":[" + terms(12) + "]"))).get(0).searchTerms()).hasSize(12);

        assertThatThrownBy(() -> parser.parse(json.readTree(subject("\"search_terms\":[" + terms(13) + "]"))))
                .isInstanceOf(InvalidRequirementListException.class)
                .hasMessageContaining("requirements[0].search_terms must contain at most 12 terms");
    }

    @Test
    void aTermOfMoreThanFortyCharactersIsRejectedNamingItsPosition() {
        assertThatThrownBy(() -> parser.parse(json.readTree("{\"requirements\":[" + item("CA-1", "SUBJECT", null, null, null) + ","
                + item("CA-2", "SUBJECT", null, null, null, "\"search_terms\":[\"ok\",\"" + "x".repeat(41) + "\"]") + "]}")))
                .hasMessageContaining("requirements[1].search_terms[1] must be at most 40 characters");
        assertThat(parser.parse(subject("\"search_terms\":[\"" + "x".repeat(40) + "\"]")).get(0).searchTerms()).hasSize(1);
    }

    @Test
    void searchTermsThatAreNotAnArrayOfStringsAreRejected() {
        assertThatThrownBy(() -> parser.parse(json.readTree(subject("\"search_terms\":\"practicum\""))))
                .hasMessageContaining("requirements[0].search_terms must be an array of strings");
        assertThatThrownBy(() -> parser.parse(json.readTree(subject("\"search_terms\":[\"practicum\",7]"))))
                .hasMessageContaining("requirements[0].search_terms must be an array of strings");
    }

    @Test
    void theModelsOutputIsHeldToTheSchemaLimitOfEightTermsButAReviewersListMayHaveTwelve() {
        assertThatThrownBy(() -> parser.parse(subject("\"search_terms\":[" + terms(9) + "]")))
                .isInstanceOf(InvalidRequirementListException.class).hasMessageContaining("search_terms");
        assertThat(parser.parse(json.readTree(subject("\"search_terms\":[" + terms(9) + "]"))).get(0).searchTerms()).hasSize(9);
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
