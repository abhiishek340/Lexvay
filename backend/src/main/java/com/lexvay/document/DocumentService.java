package com.lexvay.document;

import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobType;
import com.lexvay.program.CourseRepository;
import com.lexvay.program.ProgramService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    public record UploadResult(UUID documentId, UUID jobId) {
    }

    public record PageSummary(int pageNumber, int charStart, int charEnd, int length, TextSource textSource,
                              Double ocrConfidence) {
    }

    public record IngestStatus(UUID jobId, String status, String error) {
    }

    public record DocumentView(UUID id, UUID programId, UUID courseVersionId, String filename, String sha256,
                               DocumentKind kind, java.time.OffsetDateTime createdAt, UUID createdBy,
                               List<PageSummary> pages, IngestStatus ingest) {
    }

    private final DocumentRepository documents;
    private final ProgramService programs;
    private final CourseRepository courses;
    private final FileStore files;
    private final JobRepository jobs;
    private final AuditService audit;

    public DocumentService(DocumentRepository documents, ProgramService programs, CourseRepository courses,
                           FileStore files, JobRepository jobs, AuditService audit) {
        this.documents = documents;
        this.programs = programs;
        this.courses = courses;
        this.files = files;
        this.jobs = jobs;
        this.audit = audit;
    }

    @Transactional
    public UploadResult upload(UUID programId, MultipartFile file, DocumentKind kind, UUID courseVersionId) {
        programs.get(programId);
        if (kind == DocumentKind.RULE_TEXT) {
            throw new IllegalArgumentException("Rule text is entered through rule versions, not uploaded to a program");
        }
        byte[] bytes = read(file);
        if (bytes.length == 0) {
            throw new IllegalArgumentException("File is empty");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        FileType.detect(filename, bytes);
        if (courseVersionId != null) {
            var version = courses.findVersion(courseVersionId)
                    .orElseThrow(() -> new IllegalArgumentException("Course version not found"));
            if (!version.programId().equals(programId)) {
                throw new IllegalArgumentException("Course version belongs to a different program");
            }
        }
        String sha256 = sha256(bytes);
        documents.findDuplicate(programId, sha256).ifPresent(existing -> {
            throw new DuplicateDocumentException(existing.id());
        });

        UUID tenantId = TenantContext.require();
        UUID actor = CurrentUser.require().id();
        UUID documentId = UUID.randomUUID();
        String storageKey = tenantId + "/" + documentId;
        files.put(storageKey, bytes);
        try {
            documents.insert(documentId, tenantId, programId, courseVersionId, filename, sha256, storageKey, kind, actor);
        } catch (DuplicateKeyException race) {
            throw new DuplicateDocumentException(documents.findDuplicate(programId, sha256).map(Document::id).orElseThrow());
        }
        audit.record("DOCUMENT_UPLOADED", "document", documentId, Map.of(
                "filename", filename, "kind", kind.name(), "sha256", sha256, "bytes", bytes.length,
                "programId", programId.toString()));
        UUID jobId = jobs.enqueue(JobType.INGEST_DOCUMENT, Map.of("documentId", documentId.toString()));
        return new UploadResult(documentId, jobId);
    }

    public DocumentView get(UUID id) {
        Document document = documents.findById(id).orElseThrow(() -> new NotFoundException("Document not found"));
        List<PageSummary> pages = documents.pages(id).stream()
                .map(p -> new PageSummary(p.pageNumber(), p.charStart(), p.charEnd(), p.text().length(), p.textSource(),
                        p.ocrConfidence()))
                .toList();
        Optional<IngestStatus> ingest = jobs.latest(JobType.INGEST_DOCUMENT, "documentId", id)
                .map(j -> new IngestStatus(j.id(), j.status().name(), j.error()));
        return new DocumentView(document.id(), document.programId(), document.courseVersionId(), document.filename(),
                document.sha256(), document.kind(), document.createdAt(), document.createdBy(), pages, ingest.orElse(null));
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read upload", e);
        }
    }
}
