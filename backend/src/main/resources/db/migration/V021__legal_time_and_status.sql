-- Legal time and status (RG-1, docs/decisions/RG-1.md). Nothing becomes law because a crawler saw it: a rule version
-- has a legal status and dates that a person records with a citation, and the time a text was observed is a
-- separate fact. Three changes:
--   1. rule_version gains its legal status and dates, as creation values. They are never edited in place; a
--      correction is a new, append-only row in rule_version_status, and the view rule_version_current joins the
--      newest of them to the version (D-552).
--   2. rule_source_check can keep every change it observes while a draft waits: CHANGED_QUEUED replaces DRAFT_EXISTS
--      and carries the observed document and the waiting draft (D-555).
--   3. assessment records the date it was assessed as of, which selects the rule version in force (D-556).
--
-- The backfills touch rows of every tenant, so for the duration of this migration the owner is exempt from
-- row-level security and the trigger that limits rule_version to its approval columns is switched off. Both are
-- restored below, in the same transaction.
ALTER TABLE rule_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE rule_version DISABLE TRIGGER rule_version_approval;
ALTER TABLE assessment NO FORCE ROW LEVEL SECURITY;

ALTER TABLE rule_version
  ALTER COLUMN effective_from DROP NOT NULL,
  ADD COLUMN effective_to   date,
  ADD COLUMN published_at   date,
  ADD COLUMN adopted_at     date,
  ADD COLUMN legal_status   text NOT NULL DEFAULT 'UNKNOWN'
    CONSTRAINT rule_version_legal_status CHECK (legal_status IN
      ('PROPOSED', 'FINAL_NOT_EFFECTIVE', 'IN_FORCE', 'SUPERSEDED', 'REPEALED', 'WITHDRAWN', 'UNKNOWN')),
  ADD COLUMN dates_basis    text,
  ADD COLUMN observed_at    timestamptz;

-- Existing versions: an approved one was in force, as far as anyone recorded; its date was typed in at creation.
UPDATE rule_version
SET legal_status = CASE WHEN requirements_approved_at IS NOT NULL THEN 'IN_FORCE' ELSE 'UNKNOWN' END,
    dates_basis = 'Entered at creation before V021';

ALTER TABLE rule_version
  ADD CONSTRAINT rule_version_dates_basis_text CHECK (dates_basis IS NULL OR (length(btrim(dates_basis)) > 0 AND length(dates_basis) <= 1000)),
  ADD CONSTRAINT rule_version_dates_need_basis CHECK (dates_basis IS NOT NULL OR
      (effective_from IS NULL AND effective_to IS NULL AND published_at IS NULL AND adopted_at IS NULL)),
  ADD CONSTRAINT rule_version_effective_period CHECK (effective_to > effective_from);

ALTER TABLE assessment
  ADD COLUMN assessment_as_of date;
UPDATE assessment SET assessment_as_of = (created_at AT TIME ZONE 'UTC')::date;
ALTER TABLE assessment
  ALTER COLUMN assessment_as_of SET NOT NULL,
  ALTER COLUMN assessment_as_of SET DEFAULT CURRENT_DATE;

ALTER TABLE assessment FORCE ROW LEVEL SECURITY;
ALTER TABLE rule_version ENABLE TRIGGER rule_version_approval;
ALTER TABLE rule_version FORCE ROW LEVEL SECURITY;

-- Corrections of status and dates (product rule 5): the newest row is the current status; none is ever changed.
CREATE TABLE rule_version_status (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        uuid NOT NULL REFERENCES tenant(id),
  rule_version_id  uuid NOT NULL REFERENCES rule_version(id),
  legal_status     text NOT NULL CHECK (legal_status IN
                     ('PROPOSED', 'FINAL_NOT_EFFECTIVE', 'IN_FORCE', 'SUPERSEDED', 'REPEALED', 'WITHDRAWN', 'UNKNOWN')),
  effective_from   date,
  effective_to     date,
  published_at     date,
  adopted_at       date,
  dates_basis      text CHECK (dates_basis IS NULL OR (length(btrim(dates_basis)) > 0 AND length(dates_basis) <= 1000)),
  recorded_by      uuid NOT NULL REFERENCES app_user(id),
  recorded_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT rule_version_status_period CHECK (effective_to > effective_from),
  CONSTRAINT rule_version_status_dates_need_basis CHECK (dates_basis IS NOT NULL OR
      (effective_from IS NULL AND effective_to IS NULL AND published_at IS NULL AND adopted_at IS NULL))
);
-- No index on (rule_version_id, recorded_at): a version has a handful of status rows, and the planner reads them through
-- the tenant index below (IndexUsageTest tried one and ignored it, V008's rule is to create only what it chooses).

ALTER TABLE rule_version_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE rule_version_status FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON rule_version_status
  USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());
CREATE INDEX rule_version_status_tenant_idx ON rule_version_status (tenant_id);
CREATE TRIGGER rule_version_status_immutable BEFORE UPDATE OR DELETE ON rule_version_status
  FOR EACH ROW EXECUTE FUNCTION forbid_change();

-- A rule version as every reader sees it: its own columns, with the legal status and dates of the newest
-- rule_version_status row, or its creation values while there is none. The caller's row-level security applies.
CREATE VIEW rule_version_current WITH (security_invoker = true) AS
SELECT rv.id, rv.tenant_id, rv.jurisdiction_id, rv.license_type, rv.label, rv.source_url, rv.supersedes_id, rv.document_id,
       rv.requirements_approved_by, rv.requirements_approved_at, rv.observed_at, rv.created_at, rv.created_by,
       cur.legal_status, cur.effective_from, cur.effective_to, cur.published_at, cur.adopted_at, cur.dates_basis
FROM rule_version rv
CROSS JOIN LATERAL (
  SELECT x.legal_status, x.effective_from, x.effective_to, x.published_at, x.adopted_at, x.dates_basis
  FROM (SELECT 0 AS is_correction, rv.created_at AS at, rv.id AS row_id, rv.legal_status, rv.effective_from,
               rv.effective_to, rv.published_at, rv.adopted_at, rv.dates_basis
        UNION ALL
        SELECT 1, s.recorded_at, s.id, s.legal_status, s.effective_from, s.effective_to, s.published_at, s.adopted_at,
               s.dates_basis
        FROM rule_version_status s WHERE s.rule_version_id = rv.id) x
  ORDER BY x.is_correction DESC, x.at DESC, x.row_id DESC
  LIMIT 1) cur;

-- Every changed observation is kept (A, B, C while a draft waits). CHANGED_QUEUED carries the document and the
-- waiting draft; DRAFT_EXISTS stays valid only for the checks recorded before this migration, which stored nothing.
ALTER TABLE rule_source_check
  DROP CONSTRAINT rule_source_check_outcome_check,
  DROP CONSTRAINT rule_source_check_changed_has_both,
  DROP CONSTRAINT rule_source_check_only_changed_has_either;
ALTER TABLE rule_source_check
  ADD CONSTRAINT rule_source_check_outcome_check
    CHECK (outcome IN ('UNCHANGED', 'CHANGED', 'CHANGED_QUEUED', 'DRAFT_EXISTS', 'FETCH_FAILED')),
  ADD CONSTRAINT rule_source_check_changed_has_both
    CHECK ((outcome IN ('CHANGED', 'CHANGED_QUEUED')) = (document_id IS NOT NULL AND rule_version_id IS NOT NULL)),
  ADD CONSTRAINT rule_source_check_only_changed_has_either
    CHECK (outcome IN ('CHANGED', 'CHANGED_QUEUED') OR (document_id IS NULL AND rule_version_id IS NULL));
-- The observations queued behind a draft are read through the tenant index too: a source has a few checks a year
-- (IndexUsageTest tried a partial index on them and the planner ignored it).
