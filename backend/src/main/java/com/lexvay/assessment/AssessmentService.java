package com.lexvay.assessment;

import com.lexvay.assessment.match.EvidenceMatcher;
import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobType;
import com.lexvay.llm.LlmClient;
import com.lexvay.program.Program;
import com.lexvay.program.ProgramService;
import com.lexvay.rule.Jurisdiction;
import com.lexvay.rule.JurisdictionRepository;
import com.lexvay.rule.RuleVersionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentService {

    public record StartResult(UUID assessmentId, UUID jobId) {
    }

    public record JurisdictionSummary(String jurisdiction, long total, long evidenceFound, long evidenceMissing, long ambiguous,
                                      long decided) {
    }

    public record AssessmentView(UUID id, UUID programId, AssessmentStatus status, java.time.OffsetDateTime startedAt,
                                 java.time.OffsetDateTime finishedAt, String llmModel, String promptVersion,
                                 List<JurisdictionSummary> jurisdictions, java.time.OffsetDateTime createdAt) {
    }

    public record MappingsView(UUID assessmentId, String jurisdiction, List<Mapping> mappings) {
    }

    private final AssessmentRepository assessments;
    private final MappingRepository mappings;
    private final ProgramService programs;
    private final JurisdictionRepository jurisdictions;
    private final RuleVersionRepository ruleVersions;
    private final JobRepository jobs;
    private final LlmClient llm;
    private final AuditService audit;

    public AssessmentService(AssessmentRepository assessments, MappingRepository mappings, ProgramService programs,
                             JurisdictionRepository jurisdictions, RuleVersionRepository ruleVersions, JobRepository jobs,
                             LlmClient llm, AuditService audit) {
        this.assessments = assessments;
        this.mappings = mappings;
        this.programs = programs;
        this.jurisdictions = jurisdictions;
        this.ruleVersions = ruleVersions;
        this.jobs = jobs;
        this.llm = llm;
        this.audit = audit;
    }

    /** Refuses to start unless every target jurisdiction has an approved rule version (D-050). */
    @Transactional
    public StartResult start(UUID programId) {
        Program program = programs.get(programId);
        if (program.jurisdictions().isEmpty()) {
            throw new ConflictException("The program has no target jurisdictions");
        }
        List<String> missing = new ArrayList<>();
        for (Jurisdiction j : jurisdictions.findByCodes(program.jurisdictions())) {
            if (ruleVersions.findApprovedVersion(j.id(), program.licenseType(), LocalDate.now()).isEmpty()) {
                missing.add(j.code());
            }
        }
        if (!missing.isEmpty()) {
            throw new ConflictException("No approved rule version for license type '" + program.licenseType()
                    + "' in: " + String.join(", ", missing) + ". Approve the requirement lists first");
        }
        UUID id = UUID.randomUUID();
        UUID actor = CurrentUser.require().id();
        assessments.insert(id, TenantContext.require(), programId, llm.modelId(), EvidenceMatcher.PROMPT_VERSION, actor);
        audit.record("ASSESSMENT_STARTED", "assessment", id, Map.of(
                "programId", programId.toString(), "jurisdictions", program.jurisdictions(), "llmModel", llm.modelId(),
                "promptVersion", EvidenceMatcher.PROMPT_VERSION));
        UUID jobId = jobs.enqueue(JobType.RUN_ASSESSMENT, Map.of("assessmentId", id.toString()));
        return new StartResult(id, jobId);
    }

    public Assessment require(UUID id) {
        return assessments.findById(id).orElseThrow(() -> new NotFoundException("Assessment not found"));
    }

    public AssessmentView view(UUID id) {
        Assessment a = require(id);
        Map<String, long[]> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : assessments.findingCounts(id)) {
            long[] c = counts.computeIfAbsent((String) row.get("jurisdiction"), k -> new long[4]);
            long n = ((Number) row.get("n")).longValue();
            switch (Finding.valueOf((String) row.get("finding"))) {
                case EVIDENCE_FOUND -> c[0] += n;
                case EVIDENCE_MISSING -> c[1] += n;
                case AMBIGUOUS -> c[2] += n;
            }
            c[3] += ((Number) row.get("decided")).longValue();
        }
        List<JurisdictionSummary> summaries = counts.entrySet().stream()
                .map(e -> new JurisdictionSummary(e.getKey(), e.getValue()[0] + e.getValue()[1] + e.getValue()[2],
                        e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
        return new AssessmentView(a.id(), a.programId(), a.status(), a.startedAt(), a.finishedAt(), a.llmModel(),
                a.promptVersion(), summaries, a.createdAt());
    }

    public MappingsView mappings(UUID assessmentId, String jurisdiction) {
        require(assessmentId);
        if (jurisdiction == null || jurisdiction.isBlank()) {
            return new MappingsView(assessmentId, null, mappings.findByAssessment(assessmentId));
        }
        return new MappingsView(assessmentId, jurisdiction, mappings.findByAssessmentAndJurisdiction(assessmentId, jurisdiction));
    }
}
