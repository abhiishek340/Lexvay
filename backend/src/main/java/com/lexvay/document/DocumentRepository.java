package com.lexvay.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private static final String COLUMNS = "id, program_id, course_version_id, filename, sha256, storage_key, kind, created_at, created_by";

    private static final RowMapper<Document> DOCUMENT = (rs, i) -> new Document(
            rs.getObject("id", UUID.class),
            rs.getObject("program_id", UUID.class),
            rs.getObject("course_version_id", UUID.class),
            rs.getString("filename"),
            rs.getString("sha256"),
            rs.getString("storage_key"),
            DocumentKind.valueOf(rs.getString("kind")),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("created_by", UUID.class));

    private static final RowMapper<DocumentPage> PAGE = (rs, i) -> new DocumentPage(
            rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class),
            rs.getInt("page_number"),
            rs.getString("text"),
            rs.getInt("char_start"),
            rs.getInt("char_end"));

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, UUID programId, UUID courseVersionId, String filename, String sha256,
                       String storageKey, DocumentKind kind, UUID createdBy) {
        jdbc.sql("INSERT INTO document (id, tenant_id, program_id, course_version_id, filename, sha256, storage_key, kind, created_by) "
                        + "VALUES (:id, :tenantId, :programId, :courseVersionId, :filename, :sha256, :storageKey, :kind, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("programId", programId)
                .param("courseVersionId", courseVersionId).param("filename", filename).param("sha256", sha256)
                .param("storageKey", storageKey).param("kind", kind.name()).param("createdBy", createdBy)
                .update();
    }

    public Optional<Document> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM document WHERE id = :id").param("id", id).query(DOCUMENT).optional();
    }

    public Optional<Document> findDuplicate(UUID programId, String sha256) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM document WHERE program_id IS NOT DISTINCT FROM :programId AND sha256 = :sha256")
                .param("programId", programId).param("sha256", sha256).query(DOCUMENT).optional();
    }

    public List<Document> findByProgram(UUID programId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM document WHERE program_id = :programId ORDER BY created_at, id")
                .param("programId", programId).query(DOCUMENT).list();
    }

    public List<Document> findByCourseVersion(UUID courseVersionId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM document WHERE course_version_id = :cv ORDER BY created_at, id")
                .param("cv", courseVersionId).query(DOCUMENT).list();
    }

    /** Idempotent: a retried ingest does not duplicate or overwrite pages. */
    public int insertPage(UUID tenantId, UUID documentId, ExtractedPage page, UUID createdBy) {
        return jdbc.sql("INSERT INTO document_page (tenant_id, document_id, page_number, text, char_start, char_end, created_by) "
                        + "VALUES (:tenantId, :documentId, :pageNumber, :text, :charStart, :charEnd, :createdBy) "
                        + "ON CONFLICT (document_id, page_number) DO NOTHING")
                .param("tenantId", tenantId).param("documentId", documentId).param("pageNumber", page.pageNumber())
                .param("text", page.text()).param("charStart", page.charStart()).param("charEnd", page.charEnd())
                .param("createdBy", createdBy)
                .update();
    }

    public List<DocumentPage> pages(UUID documentId) {
        return jdbc.sql("SELECT id, document_id, page_number, text, char_start, char_end FROM document_page "
                        + "WHERE document_id = :documentId ORDER BY page_number")
                .param("documentId", documentId).query(PAGE).list();
    }

    public Optional<DocumentPage> page(UUID documentId, int pageNumber) {
        return jdbc.sql("SELECT id, document_id, page_number, text, char_start, char_end FROM document_page "
                        + "WHERE document_id = :documentId AND page_number = :pageNumber")
                .param("documentId", documentId).param("pageNumber", pageNumber).query(PAGE).optional();
    }
}
