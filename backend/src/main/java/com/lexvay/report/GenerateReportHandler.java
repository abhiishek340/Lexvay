package com.lexvay.report;

import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import com.lexvay.determination.Determination;
import com.lexvay.determination.DeterminationRepository;
import com.lexvay.document.FileStore;
import com.lexvay.job.Job;
import com.lexvay.job.JobHandler;
import com.lexvay.job.JobType;
import com.lexvay.job.PermanentJobFailure;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GenerateReportHandler implements JobHandler {

    private final ReportRepository reports;
    private final DeterminationRepository determinations;
    private final ReportService service;
    private final FileStore files;
    private final AuditService audit;

    public GenerateReportHandler(ReportRepository reports, DeterminationRepository determinations, ReportService service,
                                 FileStore files, AuditService audit) {
        this.reports = reports;
        this.determinations = determinations;
        this.service = service;
        this.files = files;
        this.audit = audit;
    }

    @Override
    public JobType type() {
        return JobType.GENERATE_REPORT;
    }

    @Override
    public void handle(Job job, Map<String, Object> payload) {
        UUID reportId = UUID.fromString(String.valueOf(payload.get("reportId")));
        Report report = reports.findById(reportId).orElseThrow(() -> new PermanentJobFailure("Report " + reportId + " not found"));
        if (report.status() == ReportStatus.READY) {
            return;
        }
        Determination determination = determinations.findById(report.determinationId())
                .orElseThrow(() -> new PermanentJobFailure("Determination missing for report " + reportId));
        byte[] pdf = PdfTextWriter.render(service.content(determination));
        String key = TenantContext.require() + "/report-" + reportId;
        files.put(key, pdf);
        reports.markReady(reportId, key);
        audit.recordAs(null, "REPORT_GENERATED", "report", reportId, Map.of("jobId", job.id().toString(),
                "determinationId", determination.id().toString(), "bytes", pdf.length));
    }
}
