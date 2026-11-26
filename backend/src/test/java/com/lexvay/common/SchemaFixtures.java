package com.lexvay.common;

import java.sql.Types;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Inserts one complete chain of rows for a tenant so immutability and isolation tests work on real
 * data: program → course → course version → document → page → rule version → requirement →
 * assessment → evidence span → mapping → review event → determination (+ course version link), plus
 * one evidence response, one llm_call and one audit row. All inserts run under the tenant's context through the normal
 * (RLS-bound) application connection.
 */
public class SchemaFixtures {

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public SchemaFixtures(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public record Chain(UUID tenantId, UUID userId, UUID programId, UUID courseId, UUID courseVersionId, UUID documentId,
                        UUID pageId, UUID jurisdictionId, UUID ruleDocumentId, UUID ruleVersionId, UUID requirementId,
                        UUID assessmentId, UUID evidenceSpanId, UUID mappingId, UUID reviewEventId,
                        UUID determinationId, UUID llmCallId, UUID auditId, UUID evidenceResponseId) {
    }

    private static final String SHA = "a".repeat(64);
    private static final String HASH = "b".repeat(64);

    public Chain insertChain(UUID tenantId, UUID userId) {
        return TenantContext.runAs(tenantId, () -> transaction.execute(status -> {
            UUID programId = UUID.randomUUID();
            jdbc.sql("INSERT INTO program (id, tenant_id, name, license_type, catalog_year, created_by) "
                            + "VALUES (:id, :t, 'Fixture program', 'FIXTURE_LICENSE', '2026-27', :u)")
                    .param("id", programId).param("t", tenantId).param("u", userId).update();

            UUID jurisdictionId = jdbc.sql("SELECT id FROM jurisdiction WHERE code = 'CA'").query(UUID.class).single();
            jdbc.sql("INSERT INTO program_jurisdiction (program_id, jurisdiction_id, tenant_id, created_by) VALUES (:p, :j, :t, :u)")
                    .param("p", programId).param("j", jurisdictionId).param("t", tenantId).param("u", userId).update();

            UUID courseId = UUID.randomUUID();
            jdbc.sql("INSERT INTO course (id, tenant_id, program_id, code, title, created_by) "
                            + "VALUES (:id, :t, :p, 'FIX 101', 'Fixture course', :u)")
                    .param("id", courseId).param("t", tenantId).param("p", programId).param("u", userId).update();

            UUID courseVersionId = UUID.randomUUID();
            jdbc.sql("INSERT INTO course_version (id, tenant_id, course_id, catalog_year, required, created_by) "
                            + "VALUES (:id, :t, :c, '2026-27', true, :u)")
                    .param("id", courseVersionId).param("t", tenantId).param("c", courseId).param("u", userId).update();

            UUID documentId = UUID.randomUUID();
            jdbc.sql("INSERT INTO document (id, tenant_id, program_id, course_version_id, filename, sha256, storage_key, kind, created_by) "
                            + "VALUES (:id, :t, :p, :cv, 'fixture.pdf', :sha, :key, 'SYLLABUS', :u)")
                    .param("id", documentId).param("t", tenantId).param("p", programId).param("cv", courseVersionId)
                    .param("sha", SHA).param("key", tenantId + "/" + documentId).param("u", userId).update();

            String pageText = "Fictional. Prototype data. This course covers professional ethics and legal standards.";
            UUID pageId = UUID.randomUUID();
            jdbc.sql("INSERT INTO document_page (id, tenant_id, document_id, page_number, text, char_start, char_end, created_by) "
                            + "VALUES (:id, :t, :d, 1, :text, 0, :end, :u)")
                    .param("id", pageId).param("t", tenantId).param("d", documentId).param("text", pageText)
                    .param("end", pageText.length()).param("u", userId).update();

            UUID ruleDocumentId = UUID.randomUUID();
            jdbc.sql("INSERT INTO document (id, tenant_id, filename, sha256, storage_key, kind, created_by) "
                            + "VALUES (:id, :t, 'rule.txt', :sha, :key, 'RULE_TEXT', :u)")
                    .param("id", ruleDocumentId).param("t", tenantId).param("sha", "c".repeat(64))
                    .param("key", tenantId + "/" + ruleDocumentId).param("u", userId).update();

            UUID ruleVersionId = UUID.randomUUID();
            jdbc.sql("INSERT INTO rule_version (id, tenant_id, jurisdiction_id, license_type, label, source_url, effective_from, document_id, created_by) "
                            + "VALUES (:id, :t, :j, 'FIXTURE_LICENSE', 'Demo rule set (fixture)', 'https://example.invalid/rule', :from, :d, :u)")
                    .param("id", ruleVersionId).param("t", tenantId).param("j", jurisdictionId)
                    .param("from", LocalDate.of(2026, 1, 1)).param("d", ruleDocumentId).param("u", userId).update();

            UUID requirementId = UUID.randomUUID();
            jdbc.sql("INSERT INTO requirement (id, tenant_id, rule_version_id, ref, title, text, type, created_by) "
                            + "VALUES (:id, :t, :rv, 'FIX-1', 'Ethics', 'Coursework in professional ethics.', 'SUBJECT', :u)")
                    .param("id", requirementId).param("t", tenantId).param("rv", ruleVersionId).param("u", userId).update();

            UUID assessmentId = UUID.randomUUID();
            jdbc.sql("INSERT INTO assessment (id, tenant_id, program_id, status, llm_model, prompt_version, created_by) "
                            + "VALUES (:id, :t, :p, 'FINISHED', 'fake', 'match-v1', :u)")
                    .param("id", assessmentId).param("t", tenantId).param("p", programId).param("u", userId).update();

            UUID evidenceSpanId = UUID.randomUUID();
            int start = pageText.indexOf("professional ethics");
            jdbc.sql("INSERT INTO evidence_span (id, tenant_id, document_id, page_number, char_start, char_end, quote, created_by) "
                            + "VALUES (:id, :t, :d, 1, :s, :e, 'professional ethics', :u)")
                    .param("id", evidenceSpanId).param("t", tenantId).param("d", documentId)
                    .param("s", start).param("e", start + "professional ethics".length()).param("u", userId).update();

            UUID mappingId = UUID.randomUUID();
            jdbc.sql("INSERT INTO mapping (id, tenant_id, assessment_id, requirement_id, jurisdiction_id, finding, method, rationale, evidence_span_id, created_by) "
                            + "VALUES (:id, :t, :a, :r, :j, 'EVIDENCE_FOUND', 'MATCH', 'Fixture rationale', :es, :u)")
                    .param("id", mappingId).param("t", tenantId).param("a", assessmentId).param("r", requirementId)
                    .param("j", jurisdictionId).param("es", evidenceSpanId).param("u", userId).update();

            UUID reviewEventId = UUID.randomUUID();
            jdbc.sql("INSERT INTO review_event (id, tenant_id, mapping_id, kind, note, user_id, created_by) "
                            + "VALUES (:id, :t, :m, 'ACCEPTED', 'Looks right', :u, :u)")
                    .param("id", reviewEventId).param("t", tenantId).param("m", mappingId).param("u", userId).update();

            UUID evidenceResponseId = UUID.randomUUID();
            jdbc.sql("INSERT INTO evidence_response (id, tenant_id, mapping_id, review_event_id, evidence_span_id, comment, created_by) "
                            + "VALUES (:id, :t, :m, :e, :es, 'Fixture comment', :u)")
                    .param("id", evidenceResponseId).param("t", tenantId).param("m", mappingId).param("e", reviewEventId)
                    .param("es", evidenceSpanId).param("u", userId).update();

            UUID determinationId = UUID.randomUUID();
            jdbc.sql("INSERT INTO determination (id, tenant_id, program_id, jurisdiction_id, rule_version_id, assessment_id, outcome, signed_by, versions_hash, created_by) "
                            + "VALUES (:id, :t, :p, :j, :rv, :a, 'MEETS', :u, :hash, :u)")
                    .param("id", determinationId).param("t", tenantId).param("p", programId).param("j", jurisdictionId)
                    .param("rv", ruleVersionId).param("a", assessmentId).param("u", userId).param("hash", HASH).update();
            jdbc.sql("INSERT INTO determination_course_version (determination_id, course_version_id, tenant_id, created_by) VALUES (:d, :cv, :t, :u)")
                    .param("d", determinationId).param("cv", courseVersionId).param("t", tenantId).param("u", userId).update();

            UUID llmCallId = UUID.randomUUID();
            jdbc.sql("INSERT INTO llm_call (id, tenant_id, assessment_id, requirement_id, purpose, model, prompt_version, request, response, created_by) "
                            + "VALUES (:id, :t, :a, :r, 'MATCH_EVIDENCE', 'fake', 'match-v1', :req, '{}', :u)")
                    .param("id", llmCallId).param("t", tenantId).param("a", assessmentId).param("r", requirementId)
                    .param("req", "{\"fixture\":true}", Types.OTHER).param("u", userId).update();

            UUID auditId = UUID.randomUUID();
            jdbc.sql("INSERT INTO audit_log (id, tenant_id, user_id, action, entity_type, entity_id, detail, created_by) "
                            + "VALUES (:id, :t, :u, 'FIXTURE', 'program', :p, '{}'::jsonb, :u)")
                    .param("id", auditId).param("t", tenantId).param("u", userId).param("p", programId).update();

            return new Chain(tenantId, userId, programId, courseId, courseVersionId, documentId, pageId, jurisdictionId,
                    ruleDocumentId, ruleVersionId, requirementId, assessmentId, evidenceSpanId, mappingId, reviewEventId,
                    determinationId, llmCallId, auditId, evidenceResponseId);
        }));
    }
}
