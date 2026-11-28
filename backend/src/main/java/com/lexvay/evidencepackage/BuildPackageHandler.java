package com.lexvay.evidencepackage;

import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import com.lexvay.determination.Determination;
import com.lexvay.determination.DeterminationRepository;
import com.lexvay.document.FileStore;
import com.lexvay.job.Job;
import com.lexvay.job.JobHandler;
import com.lexvay.job.JobType;
import com.lexvay.job.PermanentJobFailure;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds the ZIP into a temporary file, stores it whole, and only then marks the package ready, so a package that is
 * ready always has its file and one that failed has none. The temporary file is deleted in every case.
 */
@Component
class BuildPackageHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(BuildPackageHandler.class);

    private final PackageRepository packages;
    private final DeterminationRepository determinations;
    private final PackageSnapshotLoader snapshots;
    private final PackageBuilder builder;
    private final FileStore files;
    private final AuditService audit;
    private final Clock clock;

    BuildPackageHandler(PackageRepository packages, DeterminationRepository determinations, PackageSnapshotLoader snapshots,
                        PackageBuilder builder, FileStore files, AuditService audit, Clock clock) {
        this.packages = packages;
        this.determinations = determinations;
        this.snapshots = snapshots;
        this.builder = builder;
        this.files = files;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public JobType type() {
        return JobType.BUILD_PACKAGE;
    }

    @Override
    public void handle(Job job, Map<String, Object> payload) {
        UUID packageId = job.entityId();
        EvidencePackage pkg = packages.findById(packageId).orElseThrow(() -> new PermanentJobFailure("Package " + packageId + " not found"));
        if (pkg.status() != PackageStatus.QUEUED) {
            return;
        }
        Determination determination = determinations.findById(pkg.determinationId())
                .orElseThrow(() -> new PermanentJobFailure("Determination missing for package " + packageId));
        PackageSnapshot snapshot = snapshots.load(determination);
        Path temp = null;
        try {
            temp = Files.createTempFile("lexvay-package-", ".zip");
            PackageBuilder.Built built = builder.build(snapshot, packageId, Instant.now(clock), temp);
            String key = TenantContext.require() + "/package-" + packageId;
            files.put(key, temp);
            packages.markReady(packageId, key, built.sha256(), built.sizeBytes());
            audit.recordAs(null, "PACKAGE_READY", "evidence_package", packageId, Map.of("jobId", job.id().toString(),
                    "determinationId", determination.id().toString(), "bytes", built.sizeBytes(), "sha256", built.sha256()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public void onFailed(Job job) {
        packages.markFailed(job.entityId());
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete the temporary package file {}", file, e);
        }
    }
}
