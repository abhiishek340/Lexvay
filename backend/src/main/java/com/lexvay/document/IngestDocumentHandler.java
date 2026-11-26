package com.lexvay.document;

import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import com.lexvay.job.Job;
import com.lexvay.job.JobHandler;
import com.lexvay.job.JobType;
import com.lexvay.job.PermanentJobFailure;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Pipeline stage 1: extract text per page with offsets and store it. Idempotent. */
@Component
public class IngestDocumentHandler implements JobHandler {

    private final DocumentRepository documents;
    private final FileStore files;
    private final AuditService audit;

    public IngestDocumentHandler(DocumentRepository documents, FileStore files, AuditService audit) {
        this.documents = documents;
        this.files = files;
        this.audit = audit;
    }

    @Override
    public JobType type() {
        return JobType.INGEST_DOCUMENT;
    }

    @Override
    public void handle(Job job, Map<String, Object> payload) {
        UUID documentId = UUID.fromString(String.valueOf(payload.get("documentId")));
        Document document = documents.findById(documentId)
                .orElseThrow(() -> new PermanentJobFailure("Document " + documentId + " not found"));
        byte[] bytes = files.get(document.storageKey());
        List<ExtractedPage> pages;
        try {
            pages = TextExtractor.extract(FileType.fromFilename(document.filename()), bytes);
        } catch (UnparsableDocumentException | EmptyDocumentException | IllegalArgumentException e) {
            throw new PermanentJobFailure(e.getMessage(), e);
        }
        UUID tenantId = TenantContext.require();
        int inserted = 0;
        for (ExtractedPage page : pages) {
            inserted += documents.insertPage(tenantId, documentId, page, document.createdBy());
        }
        audit.recordAs(null, "DOCUMENT_INGESTED", "document", documentId, Map.of(
                "jobId", job.id().toString(), "pages", pages.size(), "pagesInserted", inserted));
    }
}
