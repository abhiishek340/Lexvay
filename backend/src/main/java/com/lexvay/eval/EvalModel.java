package com.lexvay.eval;

import com.lexvay.rule.RequirementType;
import com.lexvay.rule.RuleVersionService;
import com.lexvay.seed.ModelStrategy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/**
 * The evaluation's {@link ModelStrategy}. It remembers what extraction produced, so agreement with the
 * golden set can be scored. When {@code substitute} is set (live mode) it then replaces the model's list with
 * the approved one (the golden set's, or an answer key's) through the reviewer's replace-requirements path, so matching is scored requirement by
 * requirement. It also remembers the search terms the model proposed, to compare them with the golden
 * ones before they are replaced. Preparing the model itself is delegated: scripts for the fake, nothing for a real one.
 */
final class EvalModel implements ModelStrategy {

    private final ModelStrategy delegate;
    private final RuleVersionService ruleVersions;
    private final ObjectMapper json;
    private final RequirementList substitute;
    private final Map<String, RequirementType> extracted = new LinkedHashMap<>();
    private final Map<String, List<String>> proposedTerms = new LinkedHashMap<>();

    EvalModel(ModelStrategy delegate, RuleVersionService ruleVersions, ObjectMapper json, RequirementList substitute) {
        this.delegate = delegate;
        this.ruleVersions = ruleVersions;
        this.json = json;
        this.substitute = substitute;
    }

    /** The model's own extracted type per requirement, keyed like {@link Golden.Entry#key()}. */
    Map<String, RequirementType> extracted() {
        return extracted;
    }

    /** The model's own stored search terms per requirement, keyed like {@link #extracted()}. */
    Map<String, List<String>> proposedTerms() {
        return proposedTerms;
    }

    @Override
    public void beforeExtraction(String ruleSetLabel) {
        delegate.beforeExtraction(ruleSetLabel);
    }

    @Override
    public void afterExtraction(String jurisdiction, UUID ruleVersionId) {
        ruleVersions.requirements(ruleVersionId).requirements().forEach(r -> {
            extracted.put(Golden.key(jurisdiction, r.ref()), r.type());
            proposedTerms.put(Golden.key(jurisdiction, r.ref()), r.searchTerms());
        });
        if (substitute != null) {
            ruleVersions.replaceRequirements(ruleVersionId, substitute.requirementList(jurisdiction, json));
        }
    }

    @Override
    public void beforeMatching(Map<String, UUID> documentByFile) {
        delegate.beforeMatching(documentByFile);
    }
}
