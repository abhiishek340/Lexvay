package com.lexvay.assessment;

import com.lexvay.assessment.match.EvidenceMatcher;
import com.lexvay.assessment.rules.RulesEngine;
import com.lexvay.program.ProgramFact;
import com.lexvay.program.ProgramFactRepository;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.Requirement;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Produces one proposed mapping for one requirement: rules engine for numbers, matcher for the rest. */
@Component
public class MappingProducer {

    public record Produced(Finding finding, MappingMethod method, String rationale, UUID evidenceSpanId) {
    }

    private final ProgramFactRepository facts;
    private final EvidenceMatcher matcher;

    public MappingProducer(ProgramFactRepository facts, EvidenceMatcher matcher) {
        this.facts = facts;
        this.matcher = matcher;
    }

    public Map<NumericField, RulesEngine.Fact> currentFacts(UUID programId) {
        Map<NumericField, RulesEngine.Fact> result = new EnumMap<>(NumericField.class);
        for (ProgramFact fact : facts.currentFacts(programId)) {
            result.put(fact.key(), new RulesEngine.Fact(fact.key(), fact.value(), fact.evidenceSpanId()));
        }
        return result;
    }

    public Produced produce(UUID assessmentId, UUID programId, Requirement requirement, Map<NumericField, RulesEngine.Fact> currentFacts, UUID actor) {
        if (requirement.type().isNumeric()) {
            RulesEngine.Result result = RulesEngine.evaluate(requirement, currentFacts);
            return new Produced(result.finding(), MappingMethod.RULES_ENGINE, result.rationale(), result.evidenceSpanId());
        }
        EvidenceMatcher.Outcome outcome = matcher.match(assessmentId, programId, requirement, actor);
        return new Produced(outcome.finding(), MappingMethod.MATCH, outcome.rationale(), outcome.evidenceSpanId());
    }
}
