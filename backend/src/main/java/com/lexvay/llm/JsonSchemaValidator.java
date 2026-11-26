package com.lexvay.llm;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Validates model output against a JSON schema bundled on the classpath. */
public class JsonSchemaValidator {

    public record Result(JsonNode value, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private final ObjectMapper json;
    private final JsonNode schemaNode;
    private final Schema schema;

    public JsonSchemaValidator(ObjectMapper json, String classpathLocation) {
        this.json = json;
        try {
            this.schemaNode = json.readTree(new ClassPathResource(classpathLocation).getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read schema " + classpathLocation, e);
        }
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(json.writeValueAsString(schemaNode));
    }

    public JsonNode schema() {
        return schemaNode;
    }

    /** Parses and validates. Parse failures are reported as a single error, never thrown. */
    public Result validate(String text) {
        JsonNode node;
        try {
            node = json.readTree(text == null ? "" : text);
        } catch (RuntimeException e) {
            return new Result(null, List.of("Output is not valid JSON: " + firstLine(e.getMessage())));
        }
        List<Error> errors = schema.validate(node);
        return new Result(node, errors.stream()
                .map(e -> e.getInstanceLocation() + ": " + e.getMessage())
                .sorted().toList());
    }

    private static String firstLine(String message) {
        return message == null ? "" : message.lines().findFirst().orElse("");
    }
}
