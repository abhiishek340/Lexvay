package com.lexvay.assessment;

import com.lexvay.assessment.match.EvidenceMatcher;
import com.lexvay.assessment.rules.RulesEngine;
import com.lexvay.pathway.PathwayCoverage;
import com.lexvay.pathway.PathwayRepository;
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

    /** What a program says about itself when its requirements are assessed: the numbers people recorded and its graduation paths. */
    public record Basis(Map<NumericField, RulesEngine.Fact> facts, PathwayCoverage pathway, PathwayCoverage.Minimum pathwayMinimum) {
    }

    private final ProgramFactRepository facts;
    private final PathwayRepository pathways;
    private final EvidenceMatcher matcher;

    public MappingProducer(ProgramFactRepository facts, PathwayRepository pathways, EvidenceMatcher matcher) {
        this.facts = facts;
        this.pathways = pathways;
        this.matcher = matcher;
    }

    /** Read once for a run, so that no requirement costs another query. */
    public Basis basis(UUID programId) {
        Map<NumericField, RulesEngine.Fact> current = new EnumMap<>(NumericField.class);
        for (ProgramFact fact : facts.currentFacts(programId)) {
            current.put(fact.key(), new RulesEngine.Fact(fact.key(), fact.value(), fact.evidenceSpanId(),
                    fact.filename() + ", page " + fact.pageNumber()));
        }
        PathwayCoverage pathway = new PathwayCoverage(pathways.activeGroups(programId));
        return new Basis(current, pathway, pathway.minimumCredits());
    }

    public Produced produce(UUID assessmentId, UUID programId, Requirement requirement, Basis basis, UUID actor) {
        if (requirement.type().isNumeric()) {
            RulesEngine.Result result = RulesEngine.evaluate(requirement, basis.facts(), basis.pathwayMinimum());
            return new Produced(result.finding(), MappingMethod.RULES_ENGINE, result.rationale(), result.evidenceSpanId());
        }
        EvidenceMatcher.Outcome outcome = matcher.match(assessmentId, programId, requirement, basis.pathway(), actor);
        return new Produced(outcome.finding(), MappingMethod.MATCH, outcome.rationale(), outcome.evidenceSpanId());
    }
}
