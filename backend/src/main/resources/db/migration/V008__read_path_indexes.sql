-- Read-path indexes (WS-PERF). Every index below was chosen by the planner for the query named next to it,
-- as the application role under row-level security, on a database of about 200 tenants (IndexUsageTest,
-- docs/PERF.md). Candidates the planner ignored are not created: document (program_id) is covered by the
-- unique index (tenant_id, program_id, sha256), requirement (rule_version_id) by (rule_version_id, ref),
-- change_event (status, created_at) loses to the tenant index, and the two job payload expression indexes are
-- unusable under row-level security because jsonb ->> is not leakproof.

-- Mappings of one jurisdiction in an assessment: list, counts, undecided count.
CREATE INDEX mapping_assessment_jurisdiction_idx ON mapping (assessment_id, jurisdiction_id);
-- Marking mappings stale when a rule version is superseded (requirement -> mapping).
CREATE INDEX mapping_requirement_idx ON mapping (requirement_id);
-- Marking mappings stale when a course version is superseded (evidence span -> mapping).
CREATE INDEX mapping_evidence_span_idx ON mapping (evidence_span_id);

-- Whether a jurisdiction already has a signed determination; determinations of a program.
CREATE INDEX determination_program_jurisdiction_status_idx ON determination (program_id, jurisdiction_id, status);
-- Reopening determinations when a rule version is superseded; change events listing what they reopened.
CREATE INDEX determination_rule_version_idx ON determination (rule_version_id);

-- Documents of a course version: change handling and the ingest check before a re-run.
CREATE INDEX document_course_version_idx ON document (course_version_id);
-- Evidence spans cited from a document (stale marking and re-run by course version).
CREATE INDEX evidence_span_document_idx ON evidence_span (document_id);

-- Audit history of one record, and the audit list filtered by entity.
CREATE INDEX audit_log_entity_idx ON audit_log (entity_id);

-- Latest finished assessment of a program, on every program screen.
CREATE INDEX assessment_program_status_finished_idx ON assessment (program_id, status, finished_at DESC);
