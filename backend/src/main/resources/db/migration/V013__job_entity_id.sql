-- Job entity id (FIX-2). Every job names the one record it works on: documentId, ruleVersionId, assessmentId,
-- changeEventId, reportId or ruleSourceId, held until now as the single value of the payload object. Looking a job
-- up by that value scanned the whole job table, because `payload ->> 'key'` is not leakproof and the policies of
-- job have an OR branch for the worker flag, so the planner may not use an expression index below them (D-115).
-- The id becomes a column compared with a leakproof `=`; the payload stays for jobs that need more input.
--
-- The backfill updates rows of every tenant, so for the duration of this migration the owner is exempt from
-- row-level security. It is restored below, in the same transaction.
ALTER TABLE job NO FORCE ROW LEVEL SECURITY;

ALTER TABLE job ADD COLUMN entity_id uuid;

-- Existing rows: the single value of the payload object.
UPDATE job SET entity_id = (SELECT value::uuid FROM jsonb_each_text(payload) LIMIT 1)
WHERE jsonb_typeof(payload) = 'object';

ALTER TABLE job ALTER COLUMN entity_id SET NOT NULL;

ALTER TABLE job FORCE ROW LEVEL SECURITY;

-- Newest job of a type for one record (ingest status of a document, extraction status of a rule version) and the
-- scheduler's "is one already queued for this source".
CREATE INDEX job_entity_idx ON job (tenant_id, type, entity_id, created_at DESC);
