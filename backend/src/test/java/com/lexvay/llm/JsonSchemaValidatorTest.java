package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.rule.RequirementListParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonSchemaValidatorTest {

    JsonSchemaValidator validator = new JsonSchemaValidator(JsonMapper.builder().build(), RequirementListParser.SCHEMA);

    static final String VALID = """
            {"requirements":[
              {"ref":"UT-1","title":"Credit hours","text":"At least 60 semester hours.","type":"NUMERIC",
               "numeric_field":"TOTAL_CREDITS","numeric_operator":">=","numeric_value":60,"search_terms":[]},
              {"ref":"UT-2","title":"Ethics","text":"Coursework in professional ethics.","type":"SUBJECT",
               "numeric_field":null,"numeric_operator":null,"numeric_value":null,
               "search_terms":["code of ethics","confidentiality"]}
            ]}""";

    @Test
    void validOutputPasses() {
        var result = validator.validate(VALID);
        assertThat(result.errors()).isEmpty();
        assertThat(result.value().path("requirements")).hasSize(2);
    }

    @Test
    void invalidJsonIsOneReadableError() {
        var result = validator.validate("{not json");
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).startsWith("Output is not valid JSON");
    }

    @Test
    void unknownTypeIsRejected() {
        var result = validator.validate(VALID.replace("\"SUBJECT\"", "\"CONFIRMED_GAP\""));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("type"));
    }

    @Test
    void extraPropertyIsRejected() {
        var result = validator.validate(VALID.replace("\"ref\":\"UT-2\"", "\"ref\":\"UT-2\",\"finding\":\"EVIDENCE_FOUND\""));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("finding"));
    }

    @Test
    void nineSearchTermsAreRejectedAndEightAreAccepted() {
        String eight = "[\"a1\",\"a2\",\"a3\",\"a4\",\"a5\",\"a6\",\"a7\",\"a8\"]";
        String nine = "[\"a1\",\"a2\",\"a3\",\"a4\",\"a5\",\"a6\",\"a7\",\"a8\",\"a9\"]";

        assertThat(validator.validate(VALID.replace("[\"code of ethics\",\"confidentiality\"]", eight)).errors()).isEmpty();
        assertThat(validator.validate(VALID.replace("[\"code of ethics\",\"confidentiality\"]", nine)).errors())
                .anySatisfy(e -> assertThat(e).contains("search_terms"));
    }

    @Test
    void aSearchTermOfMoreThanFortyCharactersAndANonStringTermAreRejected() {
        String long41 = "\"" + "x".repeat(41) + "\"";

        assertThat(validator.validate(VALID.replace("\"confidentiality\"", long41)).errors()).isNotEmpty();
        assertThat(validator.validate(VALID.replace("\"confidentiality\"", "7")).errors()).isNotEmpty();
        assertThat(validator.validate(VALID.replace("\"search_terms\":[\"code of ethics\",\"confidentiality\"]", "\"search_terms\":null")).errors()).isNotEmpty();
    }

    @Test
    void searchTermsAreRequired() {
        assertThat(validator.validate(VALID.replace(",\"search_terms\":[]", "")).errors()).anySatisfy(e -> assertThat(e).contains("search_terms"));
    }

    @Test
    void missingRequiredPropertyIsRejected() {
        var result = validator.validate("{\"requirements\":[{\"ref\":\"X\",\"title\":\"T\",\"text\":\"t\",\"type\":\"SUBJECT\"}]}");
        assertThat(result.errors()).isNotEmpty();
    }
}
