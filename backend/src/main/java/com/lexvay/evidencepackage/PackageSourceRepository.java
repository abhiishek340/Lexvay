package com.lexvay.evidencepackage;

import com.lexvay.assessment.MappingRepository;
import com.lexvay.document.DocumentService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The reads only an evidence package needs: the course versions a determination is locked to and every quoted passage. */
@Repository
public class PackageSourceRepository {

    /** A course version the determination is locked to (product rule 6). */
    public record LockedCourseVersion(UUID id, String code, String title, String catalogYear) {
    }

    /**
     * One quoted evidence span with the document it comes from and the SHA-256 of the stored text of its page, which
     * lets a reader of the package confirm the quote against the page without trusting the package.
     */
    public record Quote(String requirementRef, UUID documentId, String filename, String storageKey, String documentSha256,
                        int pageNumber, int charStart, int charEnd, String quote, String pageSha256) {
    }

    private final JdbcClient jdbc;

    public PackageSourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<LockedCourseVersion> courseVersions(UUID determinationId) {
        return jdbc.sql("""
                SELECT cv.id, c.code, c.title, cv.catalog_year
                FROM determination_course_version dcv
                JOIN course_version cv ON cv.id = dcv.course_version_id
                JOIN course c ON c.id = cv.course_id
                WHERE dcv.determination_id = :d
                ORDER BY c.code, cv.id
                """).param("d", determinationId)
                .query((rs, i) -> new LockedCourseVersion(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("title"), rs.getString("catalog_year")))
                .list();
    }

    /** The quoted span of every current mapping of the assessment and jurisdiction that has one, in requirement order. */
    public List<Quote> quotes(UUID assessmentId, UUID jurisdictionId) {
        return jdbc.sql("""
                SELECT r.ref, es.document_id, d.filename, d.storage_key, d.sha256, es.page_number, es.char_start, es.char_end,
                       es.quote, dp.text AS page_text
                FROM mapping m
                JOIN requirement r ON r.id = m.requirement_id
                JOIN evidence_span es ON es.id = m.evidence_span_id
                JOIN document d ON d.id = es.document_id
                JOIN document_page dp ON dp.document_id = es.document_id AND dp.page_number = es.page_number
                WHERE m.assessment_id = :a AND m.jurisdiction_id = :j""" + MappingRepository.current("= :a")
                + " ORDER BY r.ordinal, r.ref")
                .param("a", assessmentId).param("j", jurisdictionId)
                .query((rs, i) -> new Quote(rs.getString("ref"), rs.getObject("document_id", UUID.class), rs.getString("filename"),
                        rs.getString("storage_key"), rs.getString("sha256"), rs.getInt("page_number"), rs.getInt("char_start"),
                        rs.getInt("char_end"), rs.getString("quote"),
                        DocumentService.sha256(rs.getString("page_text").getBytes(StandardCharsets.UTF_8))))
                .list();
    }
}
