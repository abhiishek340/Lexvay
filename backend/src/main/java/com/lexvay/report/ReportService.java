package com.lexvay.report;

import com.lexvay.assessment.Mapping;
import com.lexvay.assessment.MappingRepository;
import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.determination.Determination;
import com.lexvay.determination.DeterminationRepository;
import com.lexvay.document.FileStore;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobType;
import com.lexvay.program.Program;
import com.lexvay.program.ProgramRepository;
import com.lexvay.rule.Jurisdiction;
import com.lexvay.rule.JurisdictionRepository;
import com.lexvay.rule.RuleVersion;
import com.lexvay.rule.RuleVersionRepository;
import com.lexvay.tenant.DataMode;
import com.lexvay.tenant.TenantRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evidence report for a signed determination (spec section 3). Generation runs as a job. */
@Service
public class ReportService {

    public record RequestResult(UUID reportId, UUID jobId) {
    }

    private final ReportRepository reports;
    private final DeterminationRepository determinations;
    private final MappingRepository mappings;
    private final ProgramRepository programs;
    private final RuleVersionRepository ruleVersions;
    private final JurisdictionRepository jurisdictions;
    private final FileStore files;
    private final JobRepository jobs;
    private final AuditService audit;
    private final TenantRepository tenants;

    public ReportService(ReportRepository reports, DeterminationRepository determinations, MappingRepository mappings,
                         ProgramRepository programs, RuleVersionRepository ruleVersions, JurisdictionRepository jurisdictions,
                         FileStore files, JobRepository jobs, AuditService audit, TenantRepository tenants) {
        this.reports = reports;
        this.determinations = determinations;
        this.mappings = mappings;
        this.programs = programs;
        this.ruleVersions = ruleVersions;
        this.jurisdictions = jurisdictions;
        this.files = files;
        this.jobs = jobs;
        this.audit = audit;
        this.tenants = tenants;
    }

    @Transactional
    public RequestResult request(UUID determinationId) {
        determinations.findById(determinationId).orElseThrow(() -> new NotFoundException("Determination not found"));
        UUID actor = CurrentUser.require().id();
        UUID reportId = reports.insert(TenantContext.require(), determinationId, actor);
        UUID jobId = jobs.enqueue(JobType.GENERATE_REPORT, reportId, Map.of("reportId", reportId.toString()));
        audit.record("REPORT_REQUESTED", "report", reportId, Map.of("determinationId", determinationId.toString(), "jobId", jobId.toString()));
        return new RequestResult(reportId, jobId);
    }

    public Report get(UUID reportId) {
        return reports.findById(reportId).orElseThrow(() -> new NotFoundException("Report not found"));
    }

    public byte[] bytes(Report report) {
        return files.get(report.storageKey());
    }

    /** Builds the report text for a determination. Public so the job handler and tests share it. */
    public ReportContent content(Determination d) {
        Program program = programs.findById(d.programId()).orElseThrow();
        RuleVersion ruleVersion = ruleVersions.findById(d.ruleVersionId()).orElseThrow();
        Jurisdiction jurisdiction = jurisdictions.findByCodes(List.of(d.jurisdiction())).get(0);
        List<Mapping> list = mappings.findByAssessmentAndJurisdiction(d.assessmentId(), d.jurisdiction());
        List<String[]> header = new ArrayList<>();
        if (tenants.currentDataMode() == DataMode.DEMO) {
            header.add(new String[] {"Data", DataMode.DEMO_NOTICE});
        }
        header.add(new String[] {"Program", program.name() + ", catalog year " + program.catalogYear()});
        header.add(new String[] {"Jurisdiction", jurisdiction.name() + " (" + jurisdiction.code() + ")"});
        header.add(new String[] {"Outcome", d.outcome().label()});
        header.add(new String[] {"Signed by", d.signedByName() + " on " + DateTimeFormatter.ISO_LOCAL_DATE.format(d.signedAt().toLocalDate())});
        header.add(new String[] {"Status", d.status().name().equals("SIGNED") ? "Signed" : "Reopened by a later change"});
        header.add(new String[] {"Rule version", ruleVersion.label() + ", effective " + ruleVersion.effectiveFrom() + ", " + ruleVersion.sourceUrl()});
        header.add(new String[] {"Versions hash", d.versionsHash()});
        header.add(new String[] {"Course versions locked", String.valueOf(d.courseVersionIds().size())});
        header.add(new String[] {"Requirements", String.valueOf(list.size())});
        List<ReportContent.Item> items = new ArrayList<>();
        for (Mapping m : list) {
            List<String> lines = new ArrayList<>();
            lines.add("Requirement: " + m.requirement().text());
            lines.add("Finding (proposed by software): " + findingLabel(m.finding().name()) + " - " + (m.method().name().equals("RULES_ENGINE") ? "checked by code" : "passage proposed by the model and verified against the stored text"));
            if (m.decision() != null) {
                lines.add("Decision: " + decisionLabel(m.decision().kind()) + " by " + m.decision().userDisplayName()
                        + (m.decision().note() == null || m.decision().note().isBlank() ? "" : " - " + m.decision().note()));
            } else {
                lines.add("Decision: none recorded");
            }
            if (m.evidence() != null) {
                lines.add("Evidence: \"" + m.evidence().quote() + "\"");
                lines.add("Source: " + m.evidence().filename() + (m.evidence().courseCode() == null ? "" : ", " + m.evidence().courseCode())
                        + ", page " + m.evidence().pageNumber() + ", characters " + m.evidence().charStart() + "-" + m.evidence().charEnd());
            } else {
                lines.add("Evidence: none. " + m.rationale());
            }
            items.add(new ReportContent.Item(m.requirement().ref() + "  " + m.requirement().title(), lines));
        }
        String title = "Lexvay evidence report - " + (d.status().name().equals("SIGNED") ? "Signed determination" : "Determination (reopened)");
        String footer = "Findings were proposed by software and every quoted passage was verified character for character against the stored "
                + "document text. The determination above was made and signed by the named person. Report generated by Lexvay.";
        return new ReportContent(title, header, items, footer);
    }

    static String findingLabel(String finding) {
        return switch (finding) {
            case "EVIDENCE_FOUND" -> "Evidence found";
            case "EVIDENCE_MISSING" -> "Evidence missing";
            default -> "Ambiguous";
        };
    }

    static String decisionLabel(String kind) {
        return switch (kind) {
            case "ACCEPTED" -> "Accepted";
            case "EVIDENCE_REQUESTED" -> "Evidence requested";
            case "GAP_CONFIRMED" -> "Gap confirmed";
            default -> kind;
        };
    }
}
