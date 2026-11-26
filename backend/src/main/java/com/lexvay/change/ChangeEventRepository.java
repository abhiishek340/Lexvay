package com.lexvay.change;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChangeEventRepository {

    private static final RowMapper<ChangeEvent> ROW = (rs, i) -> new ChangeEvent(rs.getObject("id", UUID.class),
            ChangeKind.valueOf(rs.getString("kind")), rs.getObject("old_id", UUID.class), rs.getObject("new_id", UUID.class),
            ChangeStatus.valueOf(rs.getString("status")), rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("created_by", UUID.class));

    private final JdbcClient jdbc;

    public ChangeEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID tenantId, ChangeKind kind, UUID oldId, UUID newId, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO change_event (id, tenant_id, kind, old_id, new_id, created_by) VALUES (:id, :tenantId, :kind, :oldId, :newId, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("kind", kind.name()).param("oldId", oldId).param("newId", newId)
                .param("createdBy", createdBy).update();
        return id;
    }

    public java.util.Optional<ChangeEvent> findById(UUID id) {
        return jdbc.sql("SELECT id, kind, old_id, new_id, status, created_at, created_by FROM change_event WHERE id = :id")
                .param("id", id).query(ROW).optional();
    }

    public void markHandled(UUID id) {
        jdbc.sql("UPDATE change_event SET status = 'HANDLED' WHERE id = :id").param("id", id).update();
    }

    public ChangeEventView enrich(ChangeEvent e) {
        if (e.kind() == ChangeKind.RULE_VERSION) {
            var rows = jdbc.sql("""
                    SELECT o.label AS old_label, n.label AS new_label, j.code AS jurisdiction, (n.requirements_approved_at IS NOT NULL) AS approved
                    FROM rule_version o JOIN rule_version n ON n.id = :newId JOIN jurisdiction j ON j.id = n.jurisdiction_id WHERE o.id = :oldId
                    """).param("oldId", e.oldId()).param("newId", e.newId()).query().listOfRows();
            var row = rows.isEmpty() ? java.util.Map.<String, Object>of() : rows.get(0);
            List<UUID> reopened = jdbc.sql("SELECT id FROM determination WHERE rule_version_id = :old AND status = 'REOPENED' ORDER BY signed_at")
                    .param("old", e.oldId()).query(UUID.class).list();
            return new ChangeEventView(e.id(), e.kind(), e.oldId(), e.newId(), e.status(), e.createdAt(), (String) row.get("jurisdiction"),
                    (String) row.get("old_label"), (String) row.get("new_label"), Boolean.TRUE.equals(row.get("approved")),
                    null, null, null, false, reopened);
        }
        var rows = jdbc.sql("""
                SELECT c.code, o.catalog_year AS old_year, n.catalog_year AS new_year,
                       EXISTS (SELECT 1 FROM document d JOIN document_page p ON p.document_id = d.id WHERE d.course_version_id = n.id) AS has_doc
                FROM course_version o JOIN course_version n ON n.id = :newId JOIN course c ON c.id = n.course_id WHERE o.id = :oldId
                """).param("oldId", e.oldId()).param("newId", e.newId()).query().listOfRows();
        var row = rows.isEmpty() ? java.util.Map.<String, Object>of() : rows.get(0);
        List<UUID> reopened = jdbc.sql("""
                SELECT d.id FROM determination d JOIN determination_course_version dcv ON dcv.determination_id = d.id
                WHERE dcv.course_version_id = :old AND d.status = 'REOPENED' ORDER BY d.signed_at
                """).param("old", e.oldId()).query(UUID.class).list();
        return new ChangeEventView(e.id(), e.kind(), e.oldId(), e.newId(), e.status(), e.createdAt(), null, null, null, false,
                (String) row.get("code"), (String) row.get("old_year"), (String) row.get("new_year"), Boolean.TRUE.equals(row.get("has_doc")), reopened);
    }

    public List<ChangeEvent> findAll() {
        return jdbc.sql("SELECT id, kind, old_id, new_id, status, created_at, created_by FROM change_event ORDER BY created_at DESC, id DESC")
                .query(ROW).list();
    }
}
