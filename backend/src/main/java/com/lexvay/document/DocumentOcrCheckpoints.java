package com.lexvay.document;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The OCR checkpoints of a document, stored in {@code document_ocr_page}. Each save commits in a transaction of its
 * own, so the row survives when the ingest job's transaction rolls back on a later page. The new connection is bound
 * to the current tenant like any other ({@code TenantAwareDataSource}), so no worker flag is involved.
 */
@Component
public class DocumentOcrCheckpoints {

    private final DocumentRepository documents;
    private final TransactionTemplate independent;

    public DocumentOcrCheckpoints(DocumentRepository documents, PlatformTransactionManager transactions) {
        this.documents = documents;
        this.independent = new TransactionTemplate(transactions,
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    /** Loads every checkpoint of the document in one query. */
    public ForDocument forDocument(UUID tenantId, UUID documentId) {
        Map<Integer, DocumentRepository.OcrCheckpoint> stored = new HashMap<>();
        documents.ocrCheckpoints(documentId).forEach(checkpoint -> stored.put(checkpoint.pageNumber(), checkpoint));
        return new ForDocument(tenantId, documentId, stored);
    }

    /** The checkpoints of one document for one ingest attempt, counting the ones that were reused. */
    public final class ForDocument implements OcrCheckpoints {

        private final UUID tenantId;
        private final UUID documentId;
        private final Map<Integer, DocumentRepository.OcrCheckpoint> stored;
        private int reused;

        private ForDocument(UUID tenantId, UUID documentId, Map<Integer, DocumentRepository.OcrCheckpoint> stored) {
            this.tenantId = tenantId;
            this.documentId = documentId;
            this.stored = stored;
        }

        @Override
        public Optional<OcrPage> find(int pageNumber, int renderDpi) {
            Optional<OcrPage> found = Optional.ofNullable(stored.get(pageNumber))
                    .filter(checkpoint -> checkpoint.renderDpi() == renderDpi)
                    .map(DocumentRepository.OcrCheckpoint::page);
            if (found.isPresent()) {
                reused++;
            }
            return found;
        }

        @Override
        public void save(int pageNumber, OcrPage page, int renderDpi) {
            independent.executeWithoutResult(status ->
                    documents.insertOcrCheckpoint(tenantId, documentId, pageNumber, page, renderDpi));
        }

        /** How many pages were read from a checkpoint instead of the engine. */
        public int reused() {
            return reused;
        }
    }
}
