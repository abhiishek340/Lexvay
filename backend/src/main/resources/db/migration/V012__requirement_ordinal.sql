-- Requirement order (FIX-1). The rows of one rule version are inserted in one transaction and share a
-- timestamp, so ordering by (created_at, ref) sorted refs as text ("CO-10" before "CO-2"). The list order of
-- the extraction or of the reviewer's replacement list is now stored in `ordinal`, counted from 1.
--
-- The backfill updates rows of every tenant and of approved rule versions, so for the duration of this
-- migration the owner is exempt from row-level security and the approval lock is off. Both are restored
-- below, in the same transaction.
ALTER TABLE requirement NO FORCE ROW LEVEL SECURITY;
ALTER TABLE requirement DISABLE TRIGGER requirement_locked;

ALTER TABLE requirement ADD COLUMN ordinal integer;

-- Existing rows: creation time, then the number at the end of the ref, then the ref (refs without a number last).
UPDATE requirement r SET ordinal = o.ordinal
FROM (SELECT id, row_number() OVER (PARTITION BY rule_version_id
                                    ORDER BY created_at, (substring(ref from '(\d+)$'))::int NULLS LAST, ref) AS ordinal
      FROM requirement) o
WHERE r.id = o.id;

ALTER TABLE requirement ALTER COLUMN ordinal SET NOT NULL;
-- Also serves ORDER BY ordinal within a rule version.
ALTER TABLE requirement ADD CONSTRAINT requirement_ordinal_key UNIQUE (rule_version_id, ordinal);

ALTER TABLE requirement ENABLE TRIGGER requirement_locked;
ALTER TABLE requirement FORCE ROW LEVEL SECURITY;
