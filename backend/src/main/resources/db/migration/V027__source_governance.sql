-- Source registry governance (RG-2, docs/decisions/RG-2.md). A rule source is a governed object, not a URL someone
-- typed: it has an authority class, the hosts and paths it may be fetched from, strings its text must contain, an
-- access review, and an owner. Fetching is polite: conditional GET, one request at a time per host across instances,
-- robots.txt. Four changes:
--   1. rule_source gains its governance fields and the validators of the last good fetch (D-566, D-567).
--   2. rule_source_check has three more outcomes and records the access status the source had when it was checked.
--   3. source_host_fetch holds, per host, when it was last asked and its cached robots.txt (D-570).
--   4. job gets run_after, so a job that must wait puts itself back in the queue instead of sleeping (D-569).
--
-- The backfill touches rows of every tenant, so for the duration of this migration the owner is exempt from
-- row-level security on rule_source, and the trigger that limits which columns of rule_source may change is dropped and
-- recreated with the new columns (the pattern of V016). Both are restored below, in the same transaction.
ALTER TABLE rule_source NO FORCE ROW LEVEL SECURITY;
DROP TRIGGER rule_source_monitoring_only ON rule_source;

ALTER TABLE rule_source
  ADD COLUMN authority_class        text NOT NULL DEFAULT 'OTHER'
    CONSTRAINT rule_source_authority_class CHECK (authority_class IN
      ('STATUTE', 'ADMIN_RULE', 'REGISTER', 'BOARD_GUIDANCE', 'BOARD_FORM', 'OTHER')),
  ADD COLUMN allowed_hosts          text[],
  ADD COLUMN allowed_path_prefixes  text[] NOT NULL DEFAULT '{}',
  ADD COLUMN expected_identifiers   text[] NOT NULL DEFAULT '{}',
  ADD COLUMN access_status          text NOT NULL DEFAULT 'UNREVIEWED'
    CONSTRAINT rule_source_access_status CHECK (access_status IN ('UNREVIEWED', 'PERMITTED', 'RESTRICTED', 'BLOCKED')),
  ADD COLUMN access_note            text
    CONSTRAINT rule_source_access_note CHECK (access_note IS NULL OR length(access_note) <= 2000),
  ADD COLUMN owner_user_id          uuid REFERENCES app_user(id),
  ADD COLUMN last_etag              text CHECK (last_etag IS NULL OR length(last_etag) <= 500),
  ADD COLUMN last_modified          text CHECK (last_modified IS NULL OR length(last_modified) <= 100);

-- Existing sources: the host of their own address, class OTHER, access not reviewed. IPv6 literals keep their brackets,
-- as java.net.URI reports them.
UPDATE rule_source
SET allowed_hosts = ARRAY[lower(substring(url FROM '^https?://(?:[^/@?#]*@)?(\[[^\]]+\]|[^/:?#]+)'))];

ALTER TABLE rule_source
  ALTER COLUMN allowed_hosts SET NOT NULL,
  ADD CONSTRAINT rule_source_allowed_hosts_present CHECK (cardinality(allowed_hosts) > 0);

ALTER TABLE rule_source FORCE ROW LEVEL SECURITY;

-- Product rule 5: a source keeps its history and only changes how it is monitored and governed.
CREATE TRIGGER rule_source_monitoring_only BEFORE UPDATE ON rule_source
  FOR EACH ROW EXECUTE FUNCTION restrict_update_to_columns('active', 'last_checked_at', 'last_content_hash',
                                                           'next_check_at', 'consecutive_failures', 'last_etag', 'last_modified',
                                                           'authority_class', 'allowed_hosts', 'allowed_path_prefixes',
                                                           'expected_identifiers', 'access_status', 'access_note', 'owner_user_id');

-- Three more outcomes: the server said "not modified", robots.txt forbids the page, the text is not the rule text. The
-- check also keeps the access status the source had, so a check of a source nobody reviewed says so.
ALTER TABLE rule_source_check
  DROP CONSTRAINT rule_source_check_outcome_check;
ALTER TABLE rule_source_check
  ADD COLUMN access_status text NOT NULL DEFAULT 'UNREVIEWED'
    CONSTRAINT rule_source_check_access_status CHECK (access_status IN ('UNREVIEWED', 'PERMITTED', 'RESTRICTED', 'BLOCKED')),
  ADD CONSTRAINT rule_source_check_outcome_check
    CHECK (outcome IN ('UNCHANGED', 'CHANGED', 'CHANGED_QUEUED', 'DRAFT_EXISTS', 'FETCH_FAILED',
                       'NOT_MODIFIED', 'BLOCKED_BY_ROBOTS', 'SUSPECT_CONTENT'));

-- What each host has been asked and told. Hosts are global, not tenant data: a request to a board's server counts the
-- same whichever tenant makes it, and robots.txt is public. So the table has no tenant_id; row-level security is
-- forced and its only policy is the transaction-local worker flag (D-013), set by the one class that reads it.
CREATE TABLE source_host_fetch (
  host               text PRIMARY KEY CHECK (host = lower(host) AND length(btrim(host)) > 0 AND length(host) <= 255),
  last_fetch_at      timestamptz,
  hold_until         timestamptz,
  robots_status      integer CHECK (robots_status IS NULL OR (robots_status >= 100 AND robots_status <= 599)),
  robots_body        text CHECK (robots_body IS NULL OR length(robots_body) <= 524288),
  robots_fetched_at  timestamptz,
  CONSTRAINT source_host_fetch_robots_together CHECK ((robots_status IS NULL) = (robots_fetched_at IS NULL))
);
ALTER TABLE source_host_fetch ENABLE ROW LEVEL SECURITY;
ALTER TABLE source_host_fetch FORCE ROW LEVEL SECURITY;
CREATE POLICY worker_only ON source_host_fetch
  USING (current_setting('lexvay.worker', true) = 'on')
  WITH CHECK (current_setting('lexvay.worker', true) = 'on');

-- A job that must wait (the host was asked too recently) goes back to the queue and is claimed again from this time.
ALTER TABLE job ADD COLUMN run_after timestamptz NOT NULL DEFAULT now();
