package com.lexvay.eval;

import com.lexvay.rule.RequirementType;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A set of approved requirements that can stand in for what the model extracted, through the reviewer's
 * replace-requirements path, so that a live model is scored requirement by requirement.
 */
public interface RequirementList {

    /** The requirements of one jurisdiction in the shape the requirement-list endpoint accepts. */
    JsonNode requirementList(String jurisdiction, ObjectMapper json);

    /** One requirement as the endpoint takes it. */
    interface Item {

        String ref();

        String jurisdiction();

        String title();

        String text();

        RequirementType type();

        Golden.Numeric numeric();

        List<String> searchTerms();
    }

    static JsonNode of(List<? extends Item> items, String jurisdiction, ObjectMapper json) {
        ArrayNode requirements = json.createArrayNode();
        for (Item e : items) {
            if (!e.jurisdiction().equals(jurisdiction)) {
                continue;
            }
            ObjectNode item = requirements.addObject();
            item.put("ref", e.ref()).put("title", e.title()).put("text", e.text()).put("type", e.type().name());
            ArrayNode terms = item.putArray("search_terms");
            e.searchTerms().forEach(terms::add);
            if (e.numeric() == null) {
                item.putNull("numeric_field").putNull("numeric_operator").putNull("numeric_value");
            } else {
                item.put("numeric_field", e.numeric().field().name()).put("numeric_operator", e.numeric().operator())
                        .put("numeric_value", e.numeric().value());
            }
        }
        return json.createObjectNode().set("requirements", requirements);
    }
}
