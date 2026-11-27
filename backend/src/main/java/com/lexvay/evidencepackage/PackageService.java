package com.lexvay.evidencepackage;

import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.determination.Determination;
import com.lexvay.determination.DeterminationRepository;
import com.lexvay.determination.DeterminationStatus;
import com.lexvay.document.FileStore;
import com.lexvay.job.Job;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobType;
import com.lexvay.program.ProgramRepository;
import com.lexvay.report.ReportRepository;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Requesting, reading and downloading the evidence package of a signed determination (F15). The build is a job. */
@Service
public class PackageService {

    public record RequestResult(UUID packageId, UUID jobId) {
    }

    /** {@code error} is the reason the build failed, for a package that FAILED; otherwise null. */
    public record PackageView(UUID id, UUID determinationId, PackageStatus status, Long sizeBytes, String sha256,
                              OffsetDateTime createdAt, String error) {
    }

    /** The stored ZIP, opened; the caller closes {@code content}. */
    public record Download(InputStream content, String filename, long sizeBytes, String sha256) {
    }

    private final PackageRepository packages;
    private final DeterminationRepository determinations;
    private final ReportRepository reports;
    private final ProgramRepository programs;
    private final FileStore files;
    private final JobRepository jobs;
    private final AuditService audit;

    public PackageService(PackageRepository packages, DeterminationRepository determinations, ReportRepository reports,
                          ProgramRepository programs, FileStore files, JobRepository jobs, AuditService audit) {
        this.packages = packages;
        this.determinations = determinations;
        this.reports = reports;
        this.programs = programs;
        this.files = files;
        this.jobs = jobs;
        this.audit = audit;
    }

    @Transactional
    public RequestResult request(UUID determinationId) {
        Determination determination = determinations.findById(determinationId)
                .orElseThrow(() -> new NotFoundException("Determination not found"));
        if (determination.status() != DeterminationStatus.SIGNED) {
            throw new ConflictException("Only a signed determination can be packaged; this one was reopened by a later change");
        }
        if (reports.findLatestReady(determinationId).isEmpty()) {
            throw new ConflictException("Generate the report first");
        }
        UUID packageId = packages.insert(TenantContext.require(), determinationId, CurrentUser.require().id());
        UUID jobId = jobs.enqueue(JobType.BUILD_PACKAGE, packageId, Map.of("packageId", packageId.toString()));
        audit.record("PACKAGE_REQUESTED", "evidence_package", packageId,
                Map.of("determinationId", determinationId.toString(), "jobId", jobId.toString()));
        return new RequestResult(packageId, jobId);
    }

    @Transactional(readOnly = true)
    public PackageView get(UUID packageId) {
        EvidencePackage pkg = find(packageId);
        String error = pkg.status() == PackageStatus.FAILED ? jobs.latest(JobType.BUILD_PACKAGE, packageId).map(Job::error).orElse(null) : null;
        return new PackageView(pkg.id(), pkg.determinationId(), pkg.status(), pkg.sizeBytes(), pkg.contentSha256(), pkg.createdAt(), error);
    }

    /** Opens the stored ZIP and records that it was handed out; 409 until the build has finished. */
    @Transactional
    public Download download(UUID packageId) {
        EvidencePackage pkg = find(packageId);
        if (pkg.status() != PackageStatus.READY) {
            throw new ConflictException("The package is not ready");
        }
        Determination determination = determinations.findById(pkg.determinationId()).orElseThrow();
        String filename = "lexvay-" + slug(programs.findById(determination.programId()).orElseThrow().name()) + "-"
                + determination.jurisdiction().toLowerCase(Locale.ROOT) + "-"
                + determination.signedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate() + ".zip";
        audit.record("PACKAGE_DOWNLOADED", "evidence_package", packageId, Map.of("determinationId", determination.id().toString(),
                "bytes", pkg.sizeBytes(), "sha256", pkg.contentSha256()));
        return new Download(files.open(pkg.storageKey()), filename, pkg.sizeBytes(), pkg.contentSha256());
    }

    private EvidencePackage find(UUID packageId) {
        return packages.findById(packageId).orElseThrow(() -> new NotFoundException("Package not found"));
    }

    /** Lower-case letters and digits, one hyphen between words; "program" when nothing is left. */
    static String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "program" : slug;
    }
}
