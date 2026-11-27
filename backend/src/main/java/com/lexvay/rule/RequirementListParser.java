package com.lexvay.rule;

import com.lexvay.llm.JsonSchemaValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns JSON (model output or a reviewer's PUT body) into requirement drafts. Schema validation first,
 * then the checks the schema cannot express: non-blank strings, unique refs, numeric fields exactly
 * when the type is numeric, a known operator and a positive value. Search terms are optional (absent means none) and
 * are normalised by {@link SearchTerms}.
 */
@Component
public class RequirementListParser {

    public static final String SCHEMA = "llm/requirement-extraction.schema.json";
    private static final Set<String> OPERATORS = Set.of(">=", "<=", "=", ">", "<");
    static final int MAX_REF_LENGTH = 50;
    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_TEXT_LENGTH = 20_000;

    private final JsonSchemaValidator validator;

    public RequirementListParser(ObjectMapper json) {
        this.validator = new JsonSchemaValidator(json, SCHEMA);
    }

    public JsonNode schema() {
        return validator.schema();
    }

    public List<RequirementDraft> parse(String text) {
        JsonSchemaValidator.Result result = validator.validate(text);
        if (!result.valid()) {
            throw new InvalidRequirementListException(result.errors());
        }
        return parse(result.value());
    }

    public List<RequirementDraft> parse(JsonNode root) {
        List<String> errors = new ArrayList<>();
        List<RequirementDraft> drafts = new ArrayList<>();
        Set<String> refs = new HashSet<>();
        JsonNode items = root.path("requirements");
        if (items.isEmpty()) {
            errors.add("requirements must contain at least one entry");
        }
        int index = 0;
        for (JsonNode item : items) {
            String where = "requirements[" + index++ + "]";
            String ref = item.path("ref").asString("").trim();
            String title = item.path("title").asString("").trim();
            String body = item.path("text").asString("").trim();
            if (ref.isEmpty()) {
                errors.add(where + ".ref must not be blank");
            } else if (!refs.add(ref)) {
                errors.add(where + ".ref '" + ref + "' is duplicated");
            }
            if (title.isEmpty()) {
                errors.add(where + ".title must not be blank");
            }
            if (body.isEmpty()) {
                errors.add(where + ".text must not be blank");
            }
            requireAtMost(errors, where + ".ref", ref, MAX_REF_LENGTH);
            requireAtMost(errors, where + ".title", title, MAX_TITLE_LENGTH);
            requireAtMost(errors, where + ".text", body, MAX_TEXT_LENGTH);
            List<String> searchTerms = searchTerms(item.path("search_terms"), where + ".search_terms", errors, title, body);
            RequirementType type;
            try {
                type = RequirementType.valueOf(item.path("type").asString(""));
            } catch (IllegalArgumentException e) {
                errors.add(where + ".type is not one of the allowed values");
                continue;
            }
            JsonNode fieldNode = item.path("numeric_field");
            JsonNode operatorNode = item.path("numeric_operator");
            JsonNode valueNode = item.path("numeric_value");
            boolean anyNumeric = !isNull(fieldNode) || !isNull(operatorNode) || !isNull(valueNode);
            NumericField field = null;
            String operator = null;
            BigDecimal value = null;
            if (type.isNumeric()) {
                if (isNull(fieldNode) || isNull(operatorNode) || isNull(valueNode)) {
                    errors.add(where + " has type " + type + " and needs numeric_field, numeric_operator and numeric_value");
                    continue;
                }
                try {
                    field = NumericField.valueOf(fieldNode.asString());
                } catch (IllegalArgumentException e) {
                    errors.add(where + ".numeric_field is not one of the allowed values");
                }
                operator = operatorNode.asString();
                if (!OPERATORS.contains(operator)) {
                    errors.add(where + ".numeric_operator is not one of the allowed values");
                }
                if (!valueNode.isNumber() || !Double.isFinite(valueNode.doubleValue()) || valueNode.decimalValue().signum() <= 0) {
                    errors.add(where + ".numeric_value must be a finite positive number");
                } else {
                    value = valueNode.decimalValue();
                }
            } else if (anyNumeric) {
                errors.add(where + " has type " + type + " and must not carry numeric fields");
            }
            drafts.add(new RequirementDraft(ref, title, body, type, field, operator, value, searchTerms));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequirementListException(errors);
        }
        return drafts;
    }

    /** The normalised terms of one requirement; the problems of a malformed list are added to {@code errors}. */
    private static List<String> searchTerms(JsonNode node, String where, List<String> errors, String title, String text) {
        if (isNull(node)) {
            return List.of();
        }
        if (!node.isArray()) {
            errors.add(where + " must be an array of strings");
            return List.of();
        }
        if (node.size() > SearchTerms.MAX_STORED) {
            errors.add(where + " must contain at most " + SearchTerms.MAX_STORED + " terms");
            return List.of();
        }
        List<String> proposed = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            if (!node.get(i).isString()) {
                errors.add(where + " must be an array of strings");
                return List.of();
            }
            String term = node.get(i).asString();
            requireAtMost(errors, where + "[" + i + "]", term.trim(), SearchTerms.MAX_LENGTH);
            proposed.add(term);
        }
        return SearchTerms.normalise(proposed, title, text);
    }

    private static void requireAtMost(List<String> errors, String field, String value, int max) {
        if (value.length() > max) {
            errors.add(field + " must be at most " + max + " characters");
        }
    }

    private static boolean isNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }
}
