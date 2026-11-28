package com.lexvay.assessment;

import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import com.lexvay.job.Job;
import com.lexvay.job.JobHandler;
import com.lexvay.job.JobType;
import com.lexvay.job.PermanentJobFailure;
import com.lexvay.observability.LexvayMetrics;
import com.lexvay.program.Program;
import com.lexvay.program.ProgramRepository;
import com.lexvay.rule.Jurisdiction;
import com.lexvay.rule.JurisdictionRepository;
import com.lexvay.rule.Requirement;
import com.lexvay.rule.RuleVersion;
import com.lexvay.rule.RuleVersionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pipeline stages 3 to 5 for one assessment. Each requirement is persisted in its own transaction so a
 * crash keeps the finished work and a retry continues where it stopped (D-052). Rules-engine
 * requirements never reach the model.
 */
@Component
public class RunAssessmentHandler implements JobHandler {

    private final AssessmentRepository assessments;
    private final MappingRepository mappings;
    private final ProgramRepository programs;
    private final JurisdictionRepository jurisdictions;
    private final RuleVersionRepository ruleVersions;
    private final MappingProducer producer;
    private final AuditService audit;
    private final LexvayMetrics metrics;
    private final TransactionTemplate newTransaction;

    public RunAssessmentHandler(AssessmentRepository assessments, MappingRepository mappings, ProgramRepository programs,
                                JurisdictionRepository jurisdictions, RuleVersionRepository ruleVersions,
                                MappingProducer producer, AuditService audit, LexvayMetrics metrics,
                                PlatformTransactionManager transactionManager) {
        this.assessments = assessments;
        this.mappings = mappings;
        this.programs = programs;
        this.jurisdictions = jurisdictions;
        this.ruleVersions = ruleVersions;
        this.producer = producer;
        this.audit = audit;
        this.metrics = metrics;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public JobType type() {
        return JobType.RUN_ASSESSMENT;
    }

    @Override
    public void handle(Job job, Map<String, Object> payload) {
        UUID assessmentId = UUID.fromString(String.valueOf(payload.get("assessmentId")));
        Assessment assessment = assessments.findById(assessmentId)
                .orElseThrow(() -> new PermanentJobFailure("Assessment " + assessmentId + " not found"));
        if (assessment.status() == AssessmentStatus.FINISHED) {
            return;
        }
        Program program = programs.findById(assessment.programId())
                .orElseThrow(() -> new PermanentJobFailure("Program " + assessment.programId() + " not found"));
        newTransaction.executeWithoutResult(s -> assessments.markRunning(assessmentId));
        try {
            MappingProducer.Basis basis = producer.basis(program.id());
            UUID tenantId = TenantContext.require();
            int produced = 0;
            for (Jurisdiction jurisdiction : jurisdictions.findByCodes(program.jurisdictions())) {
                RuleVersion ruleVersion = ruleVersions.findApprovedVersion(jurisdiction.id(), program.licenseType(), LocalDate.now())
                        .orElseThrow(() -> new PermanentJobFailure("No approved rule version for " + jurisdiction.code()
                                + " and license type " + program.licenseType()));
                List<Requirement> requirements = ruleVersions.requirements(ruleVersion.id());
                for (Requirement requirement : requirements) {
                    if (mappings.exists(assessmentId, requirement.id())) {
                        continue;
                    }
                    MappingProducer.Produced stored = newTransaction.execute(s -> {
                        MappingProducer.Produced p = producer.produce(assessmentId, program.id(), requirement, basis, assessment.createdBy());
                        boolean inserted = mappings.insertIfAbsent(tenantId, assessmentId, requirement.id(), jurisdiction.id(), p.finding(),
                                p.method(), p.rationale(), p.evidenceSpanId(), assessment.createdBy());
                        return inserted ? p : null;
                    });
                    if (stored != null) {
                        metrics.mappingFinding(stored.finding(), stored.method());
                        produced++;
                    }
                }
            }
            int count = produced;
            newTransaction.executeWithoutResult(s -> {
                assessments.markFinished(assessmentId);
                audit.recordAs(null, "ASSESSMENT_FINISHED", "assessment", assessmentId, Map.of(
                        "jobId", job.id().toString(), "mappingsProduced", count));
            });
        } catch (RuntimeException e) {
            newTransaction.executeWithoutResult(s -> assessments.markFailed(assessmentId));
            throw e;
        }
    }
}
