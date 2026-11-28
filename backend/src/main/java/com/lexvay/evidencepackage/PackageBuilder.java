package com.lexvay.evidencepackage;

import com.lexvay.audit.AuditCsv;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.CsvWriter;
import com.lexvay.document.FileStore;
import com.lexvay.evidencepackage.PackageEntries.CitedDocument;
import com.lexvay.evidencepackage.PackageEntries.ManifestJson;
import com.lexvay.job.PermanentJobFailure;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the package ZIP for a determination to a file (F15). Entries go in a fixed order with a fixed time, so the
 * same inputs and the same {@code generatedAt} give the same bytes. Nothing is held whole: documents and the audit
 * trail stream through, one entry at a time.
 */
@Component
class PackageBuilder {

    /** The ZIP as stored: the hash of its bytes and their count. */
    record Built(String sha256, long sizeBytes) {
    }

    static final String HASH_ALGORITHM = "SHA-256";

    private final FileStore files;
    private final AuditRepository audit;
    private final ObjectMapper json;
    private final String lexvayVersion;
    private final long maxBytes;
    private final byte[] readme;

    PackageBuilder(FileStore files, AuditRepository audit, ObjectMapper json, BuildProperties build,
                   @Value("${lexvay.package.max-bytes}") long maxBytes) throws IOException {
        this.files = files;
        this.audit = audit;
        this.json = json;
        this.lexvayVersion = build.getVersion();
        this.maxBytes = maxBytes;
        this.readme = new ClassPathResource("evidence-package/README.txt").getContentAsByteArray();
    }

    /**
     * Builds the package into {@code target}. Fails the job for good when the file would pass the size limit, and when
     * a stored document no longer has the hash recorded at upload: a package never carries bytes it cannot vouch for.
     */
    Built build(PackageSnapshot snapshot, UUID packageId, Instant generatedAt, Path target) throws IOException {
        Instant stamp = generatedAt.truncatedTo(ChronoUnit.SECONDS);
        StoredFileOutput out = new StoredFileOutput(Files.newOutputStream(target), maxBytes);
        try (out; PackageZip zip = new PackageZip(out, stamp)) {
            writeEntries(zip, snapshot, packageId);
            zip.add("manifest.json", json.writerWithDefaultPrettyPrinter().writeValueAsBytes(new ManifestJson(packageId,
                    snapshot.determination().id(), snapshot.dataMode(), stamp, lexvayVersion, HASH_ALGORITHM, zip.entries())));
        }
        return new Built(out.sha256(), out.size());
    }

    private void writeEntries(PackageZip zip, PackageSnapshot s, UUID packageId) throws IOException {
        List<CitedDocument> documents = PackageEntries.citedDocuments(s.quotes());
        zip.add("README.txt", PackageEntries.readme(readme, s.dataMode()));
        try (InputStream pdf = files.open(s.report().storageKey())) {
            zip.add("report.pdf", pdf);
        }
        zip.add("determination.json", json.writerWithDefaultPrettyPrinter().writeValueAsBytes(PackageEntries.determination(s)));
        zip.addText("requirements.csv", out -> PackageEntries.requirements(CsvWriter.to(out), s.mappings()));
        zip.addText("quotes.csv", out -> PackageEntries.quotes(CsvWriter.to(out), s.quotes(), documents));
        zip.addText("audit.csv", out -> {
            CsvWriter csv = AuditCsv.start(out);
            var d = s.determination();
            audit.streamTrailOfDetermination(d.id(), d.assessmentId(), d.jurisdictionId(), packageId, row -> AuditCsv.write(csv, row));
        });
        for (CitedDocument document : documents) {
            try (InputStream content = files.open(document.storageKey())) {
                PackageZip.Entry entry = zip.add(document.path(), content);
                if (!entry.sha256().equals(document.sha256())) {
                    throw new PermanentJobFailure("The stored file of " + document.path() + " no longer matches the hash recorded at upload");
                }
            }
        }
    }
}
