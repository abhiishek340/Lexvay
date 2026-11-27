package com.lexvay.change;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChangeEventRepository {

    /**
     * Change event {@code ce} touches program {@code p}: a new version of one of its courses ({@code c}), or a
     * new rule version ({@code rn}) for one of its target jurisdictions and its license type.
     */
    public static final String AFFECTS_PROGRAM = """
            ((ce.kind = 'COURSE_VERSION' AND c.program_id = p.id)
             OR (ce.kind = 'RULE_VERSION' AND rn.license_type = p.license_type
                 AND rn.jurisdiction_id IN (SELECT pj.jurisdiction_id FROM program_jurisdiction pj WHERE pj.program_id = p.id)))
            """;

    private static final RowMapper<ChangeEvent> ROW = (rs, i) -> new ChangeEvent(rs.getObject("id", UUID.class),
            ChangeKind.valueOf(rs.getString("kind")), rs.getObject("old_id", UUID.class), rs.getObject("new_id", UUID.class),
            ChangeStatus.valueOf(rs.getString("status")), rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("created_by", UUID.class));

    private static final RowMapper<ChangeEventView> VIEW = (rs, i) -> new ChangeEventView(rs.getObject("id", UUID.class),
            ChangeKind.valueOf(rs.getString("kind")), rs.getObject("old_id", UUID.class), rs.getObject("new_id", UUID.class),
            ChangeStatus.valueOf(rs.getString("status")), rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getString("jurisdiction"), rs.getString("old_label"), rs.getString("new_label"), rs.getBoolean("approved"),
            rs.getString("course_code"), rs.getString("old_year"), rs.getString("new_year"), rs.getBoolean("has_doc"),
            List.of((UUID[]) rs.getArray("reopened").getArray()));

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

    public Optional<ChangeEvent> findById(UUID id) {
        return jdbc.sql("SELECT id, kind, old_id, new_id, status, created_at, created_by FROM change_event WHERE id = :id")
                .param("id", id).query(ROW).optional();
    }

    public void markHandled(UUID id) {
        jdbc.sql("UPDATE change_event SET status = 'HANDLED' WHERE id = :id").param("id", id).update();
    }

    public List<ChangeEventView> findViews() {
        return views("", Map.of());
    }

    public Optional<ChangeEventView> findViewById(UUID id) {
        return views("WHERE ce.id = :id", Map.of("id", id)).stream().findFirst();
    }

    /** Open events that touch the program ({@link #AFFECTS_PROGRAM}). */
    public List<ChangeEventView> findOpenViewsForProgram(UUID programId) {
        return views("WHERE ce.status = 'OPEN' AND EXISTS (SELECT 1 FROM program p WHERE p.id = :programId AND "
                + AFFECTS_PROGRAM + ")", Map.of("programId", programId));
    }

    /** One statement for any number of events: what each affects is joined in, not looked up per event. */
    private List<ChangeEventView> views(String where, Map<String, Object> params) {
        var spec = jdbc.sql("""
                SELECT ce.id, ce.kind, ce.old_id, ce.new_id, ce.status, ce.created_at,
                       rj.code AS jurisdiction, ro.label AS old_label, rn.label AS new_label,
                       (rn.requirements_approved_at IS NOT NULL) AS approved,
                       c.code AS course_code, co.catalog_year AS old_year, cn.catalog_year AS new_year,
                       EXISTS (SELECT 1 FROM document d JOIN document_page p ON p.document_id = d.id
                               WHERE d.course_version_id = cn.id) AS has_doc,
                       ARRAY(SELECT r.id FROM (
                                 SELECT d.id, d.signed_at FROM determination d
                                 WHERE ce.kind = 'RULE_VERSION' AND d.rule_version_id = ce.old_id AND d.status = 'REOPENED'
                                 UNION ALL
                                 SELECT d.id, d.signed_at FROM determination d
                                 JOIN determination_course_version dcv ON dcv.determination_id = d.id
                                 WHERE ce.kind = 'COURSE_VERSION' AND dcv.course_version_id = ce.old_id AND d.status = 'REOPENED') r
                             ORDER BY r.signed_at) AS reopened
                FROM change_event ce
                LEFT JOIN rule_version ro ON ce.kind = 'RULE_VERSION' AND ro.id = ce.old_id
                LEFT JOIN rule_version rn ON ce.kind = 'RULE_VERSION' AND rn.id = ce.new_id
                LEFT JOIN jurisdiction rj ON rj.id = rn.jurisdiction_id
                LEFT JOIN course_version co ON ce.kind = 'COURSE_VERSION' AND co.id = ce.old_id
                LEFT JOIN course_version cn ON ce.kind = 'COURSE_VERSION' AND cn.id = ce.new_id
                LEFT JOIN course c ON c.id = cn.course_id
                """ + where + " ORDER BY ce.created_at DESC, ce.id DESC");
        params.forEach(spec::param);
        return spec.query(VIEW).list();
    }
}
