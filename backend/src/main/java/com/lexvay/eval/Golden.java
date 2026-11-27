package com.lexvay.eval;

import com.lexvay.assessment.Finding;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.RequirementType;
import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Shape of eval/golden.yml: what a careful reviewer expects for each requirement of the seeded program. */
public record Golden(boolean fictional, List<Entry> requirements) {

    public record Entry(String ref, String jurisdiction, String title, String text, RequirementType type, Numeric numeric,
                        Finding finding, String document, String quoteContains, List<String> searchTerms,
                        String reason) {

        /** Requirements are identified by jurisdiction and ref, so two rule sets may reuse a ref. */
        public String key() {
            return Golden.key(jurisdiction, ref);
        }
    }

    public record Numeric(NumericField field, String operator, BigDecimal value) {
    }

    public static String key(String jurisdiction, String ref) {
        return jurisdiction + "/" + ref;
    }

    /** The golden requirement list of one jurisdiction in the shape the requirement-list endpoint accepts. */
    public JsonNode requirementList(String jurisdiction, ObjectMapper json) {
        ArrayNode items = json.createArrayNode();
        for (Entry e : requirements) {
            if (!e.jurisdiction().equals(jurisdiction)) {
                continue;
            }
            ObjectNode item = items.addObject();
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
        return json.createObjectNode().set("requirements", items);
    }
}
