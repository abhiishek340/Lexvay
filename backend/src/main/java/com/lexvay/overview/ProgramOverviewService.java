package com.lexvay.overview;

import com.lexvay.assessment.AssessmentService;
import com.lexvay.assessment.AssessmentService.AssessmentView;
import com.lexvay.change.ChangeEventRepository;
import com.lexvay.determination.DeterminationRepository;
import com.lexvay.program.Program;
import com.lexvay.program.ProgramService;
import com.lexvay.rule.Jurisdiction;
import com.lexvay.rule.JurisdictionRepository;
import com.lexvay.rule.RuleVersion;
import com.lexvay.rule.RuleVersionRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Composes the program screen's data inside one read-only transaction, so it is one connection and one snapshot. */
@Service
public class ProgramOverviewService {

    private final ProgramService programs;
    private final AssessmentService assessments;
    private final DeterminationRepository determinations;
    private final ChangeEventRepository changes;
    private final JurisdictionRepository jurisdictions;
    private final RuleVersionRepository ruleVersions;

    public ProgramOverviewService(ProgramService programs, AssessmentService assessments,
                                  DeterminationRepository determinations, ChangeEventRepository changes,
                                  JurisdictionRepository jurisdictions, RuleVersionRepository ruleVersions) {
        this.programs = programs;
        this.assessments = assessments;
        this.determinations = determinations;
        this.changes = changes;
        this.jurisdictions = jurisdictions;
        this.ruleVersions = ruleVersions;
    }

    @Transactional(readOnly = true)
    public ProgramOverview overview(UUID programId) {
        Program program = programs.get(programId);
        AssessmentView assessment = assessments.latestFinishedView(programId).orElse(null);
        long undecided = assessment == null ? 0 : assessment.jurisdictions().stream()
                .mapToLong(j -> j.total() - j.decided()).sum();
        return new ProgramOverview(program, assessment, determinations.findByProgram(programId),
                changes.findOpenViewsForProgram(programId), undecided, ruleStandings(program));
    }

    /** One entry per target jurisdiction, from three statements however many there are (D-556). */
    private List<RuleStanding> ruleStandings(Program program) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Jurisdiction> targets = jurisdictions.findByCodes(program.jurisdictions());
        List<UUID> ids = targets.stream().map(Jurisdiction::id).toList();
        Map<UUID, RuleVersion> inForce = ruleVersions.findInForce(ids, program.licenseType(), today);
        Map<UUID, RuleVersion> upcoming = ruleVersions.findUpcoming(ids, program.licenseType(), today);
        return targets.stream().map(j -> new RuleStanding(j.code(), ref(inForce.get(j.id())), ref(upcoming.get(j.id())),
                inForce.containsKey(j.id()) ? null : "No rule in force on " + today,
                upcoming.containsKey(j.id()) ? "Upcoming from " + upcoming.get(j.id()).legal().effectiveFrom() : null)).toList();
    }

    private static RuleStanding.RuleRef ref(RuleVersion version) {
        return version == null ? null : new RuleStanding.RuleRef(version.id(), version.label(), version.legal().effectiveFrom());
    }
}
