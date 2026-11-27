-- Rule source schedule (FIX-4). A source is due when next_check_at has passed, instead of when last_checked_at plus
-- the interval has: that lets a failing source be retried sooner than its interval, with a growing delay, and lets
-- the scheduler's due query use an index. A check sets next_check_at itself (RuleSourceRepository.markChecked, D-381).
--
-- The backfill touches rows of every tenant, so for the duration of this migration the owner is exempt from row-level
-- security, and the trigger that limits which columns of rule_source may change is dropped and recreated with the two
-- new columns. All of it is restored below, in the same transaction.
ALTER TABLE rule_source NO FORCE ROW LEVEL SECURITY;
DROP TRIGGER rule_source_monitoring_only ON rule_source;

ALTER TABLE rule_source
  ADD COLUMN next_check_at timestamptz,
  ADD COLUMN consecutive_failures integer NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0);

-- Existing rows: one interval after the last check, or due now when the source was never checked.
UPDATE rule_source SET next_check_at = coalesce(last_checked_at + check_interval_hours * interval '1 hour', now());

-- A new source is due at once: its first check is the one that sets the baseline.
ALTER TABLE rule_source ALTER COLUMN next_check_at SET NOT NULL, ALTER COLUMN next_check_at SET DEFAULT now();

ALTER TABLE rule_source FORCE ROW LEVEL SECURITY;

-- Product rule 5: a source keeps its history and only changes how it is monitored.
CREATE TRIGGER rule_source_monitoring_only BEFORE UPDATE ON rule_source
  FOR EACH ROW EXECUTE FUNCTION restrict_update_to_columns('active', 'last_checked_at', 'last_content_hash',
                                                           'next_check_at', 'consecutive_failures');

-- The scheduler's due query: active sources whose time has come. Partial, because inactive sources are never due.
CREATE INDEX rule_source_due_idx ON rule_source (next_check_at) WHERE active;
