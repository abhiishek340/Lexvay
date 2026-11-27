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

    static final int MAX_FILENAME_LENGTH = 255;

    public record UploadResult(UUID documentId, UUID jobId) {
    }

    public record PageSummary(int pageNumber, int charStart, int charEnd, int length, TextSource textSource,
                              Double ocrConfidence) {
    }

    /** A single page of extracted text with where it belongs. Pages never change once ingested. */
    public record PageView(UUID documentId, String filename, DocumentKind kind, String courseCode, Boolean courseRequired,
                           int pageNumber, int pageCount, String text, TextSource textSource, Double ocrConfidence) {
    }

    public record IngestStatus(UUID jobId, String status, String error) {
    }

    /**
     * A program document with its course context, for choosing a passage (F4). {@code current}: a program-level
     * document, or one of a course version no other version supersedes. {@code pageCount} 0: no text yet.
     */
    public record ProgramDocumentView(UUID id, String filename, DocumentKind kind, UUID courseVersionId, String courseCode,
                                      String courseTitle, String catalogYear, Boolean courseRequired, int pageCount,
                                      boolean current, java.time.OffsetDateTime createdAt) {
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
        requireUsableFilename(filename);
        FileType type = FileType.detect(filename, bytes);
        if (type == FileType.DOCX) {
            ContentLimits.requireSafeDocx(bytes);
        } else if (type == FileType.PDF) {
            ContentLimits.requirePdfPages(bytes);
        }
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
        UUID jobId = jobs.enqueue(JobType.INGEST_DOCUMENT, documentId, Map.of("documentId", documentId.toString()));
        return new UploadResult(documentId, jobId);
    }

    /**
     * Stores rule text, which belongs to no program (D-009): the file, the document row and its already extracted
     * pages (D-044), so a rule version can be extracted at once without an ingest job. The caller is a person or,
     * for a rule source check, the system (no user). Content already stored for this tenant is a
     * {@link DuplicateDocumentException} carrying the existing document. It runs in the caller's transaction and is not
     * itself transactional, so a caller that handles the duplicate does not find the transaction marked for rollback.
     */
    public UUID storeRuleText(byte[] bytes, String filename, List<ExtractedPage> pages) {
        String sha256 = sha256(bytes);
        documents.findDuplicate(null, sha256).ifPresent(existing -> {
            throw new DuplicateDocumentException(existing.id());
        });
        UUID tenantId = TenantContext.require();
        UUID actor = CurrentUser.idOrNull();
        UUID documentId = UUID.randomUUID();
        String storageKey = tenantId + "/" + documentId;
        files.put(storageKey, bytes);
        documents.insert(documentId, tenantId, null, null, filename, sha256, storageKey, DocumentKind.RULE_TEXT, actor);
        for (ExtractedPage page : pages) {
            documents.insertPage(tenantId, documentId, page, actor);
        }
        return documentId;
    }

    @Transactional(readOnly = true)
    public DocumentView get(UUID id) {
        Document document = find(id);
        List<PageSummary> pages = documents.pages(id).stream()
                .map(p -> new PageSummary(p.pageNumber(), p.charStart(), p.charEnd(), p.text().length(), p.textSource(),
                        p.ocrConfidence()))
                .toList();
        Optional<IngestStatus> ingest = jobs.latest(JobType.INGEST_DOCUMENT, id)
                .map(j -> new IngestStatus(j.id(), j.status().name(), j.error()));
        return new DocumentView(document.id(), document.programId(), document.courseVersionId(), document.filename(),
                document.sha256(), document.kind(), document.createdAt(), document.createdBy(), pages, ingest.orElse(null));
    }

    /** Every document of the program, program-level documents first, then by course and newest catalog year. */
    @Transactional(readOnly = true)
    public List<ProgramDocumentView> forProgram(UUID programId) {
        programs.get(programId);
        return documents.viewsByProgram(programId);
    }

    /** The document row, one statement; row-level security makes another tenant's document not found. */
    @Transactional(readOnly = true)
    public Document find(UUID id) {
        return documents.findById(id).orElseThrow(() -> new NotFoundException("Document not found"));
    }

    /** The original uploaded bytes from the file store; no database work, so it runs outside any transaction. */
    public byte[] content(Document document) {
        return files.get(document.storageKey());
    }

    @Transactional(readOnly = true)
    public DocumentRepository.PageWithContext page(UUID id, int pageNumber) {
        return documents.pageWithContext(id, pageNumber).orElseThrow(() -> new NotFoundException("Document page not found"));
    }

    private static void requireUsableFilename(String filename) {
        if (filename.length() > MAX_FILENAME_LENGTH || filename.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("File name must be at most " + MAX_FILENAME_LENGTH
                    + " characters and contain no control characters");
        }
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
