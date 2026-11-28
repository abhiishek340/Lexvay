package com.lexvay.eval;

import com.lexvay.assessment.Finding;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.RequirementType;
import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shape of eval/golden.yml: what a careful reviewer expects for each requirement of the seeded program. */
public record Golden(boolean fictional, List<Entry> requirements) implements RequirementList {

    public record Entry(String ref, String jurisdiction, String title, String text, RequirementType type, Numeric numeric,
                        Finding finding, String document, String quoteContains, List<String> searchTerms,
                        String reason) implements RequirementList.Item {

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
    @Override
    public JsonNode requirementList(String jurisdiction, ObjectMapper json) {
        return RequirementList.of(requirements, jurisdiction, json);
    }
}
