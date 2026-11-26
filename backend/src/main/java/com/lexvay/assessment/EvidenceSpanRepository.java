package com.lexvay.assessment;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Immutable cited passages: document, page, page-relative offsets (D-041) and the exact text. */
@Repository
public class EvidenceSpanRepository {

    private final JdbcClient jdbc;

    public EvidenceSpanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID tenantId, UUID documentId, int pageNumber, int charStart, int charEnd, String quote, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO evidence_span (id, tenant_id, document_id, page_number, char_start, char_end, quote, created_by) "
                        + "VALUES (:id, :tenantId, :documentId, :pageNumber, :charStart, :charEnd, :quote, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("documentId", documentId).param("pageNumber", pageNumber)
                .param("charStart", charStart).param("charEnd", charEnd).param("quote", quote).param("createdBy", createdBy)
                .update();
        return id;
    }
}
