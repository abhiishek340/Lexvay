package com.lexvay.report;

import com.lexvay.audit.AuditService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Checks a report file someone hands back (F14): hashes its bytes and looks the hash up among the reports of the
 * caller's tenant. The file is hashed as a stream before the transaction starts, so no connection waits on the
 * upload, and it is never stored. The lookup and its audit entry share one transaction.
 */
@Service
public class ReportCheckService {

    private static final Logger log = LoggerFactory.getLogger(ReportCheckService.class);

    private final ReportRepository reports;
    private final AuditService audit;
    private final TransactionTemplate transaction;

    public ReportCheckService(ReportRepository reports, AuditService audit, TransactionTemplate transaction) {
        this.reports = reports;
        this.audit = audit;
        this.transaction = transaction;
    }

    public ReportCheck check(MultipartFile file) {
        String sha256 = PdfFingerprint.of(open(file));
        return transaction.execute(status -> {
            List<ReportCheck.Match> found = reports.findByContent(sha256);
            if (found.size() > 1) {
                log.warn("Reports {} and {} have the same content hash {}; the check answers with the newest",
                        found.get(0).reportId(), found.get(1).reportId(), sha256);
            }
            ReportCheck.Match match = found.isEmpty() ? null : found.get(0);
            audit.record("REPORT_CHECKED", "report", match == null ? null : match.reportId(),
                    Map.of("match", match != null, "sha256", sha256));
            return ReportCheck.of(sha256, match);
        });
    }

    private static InputStream open(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the file", e);
        }
    }
}
