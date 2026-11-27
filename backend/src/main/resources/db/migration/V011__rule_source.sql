-- Rule source monitoring (F12). A tenant registers the web page or PDF where a licensing board publishes its
-- rules. A scheduled job fetches it and, when the text changed, creates an UNAPPROVED draft rule version through
-- the normal path. Nothing here can approve a version (product rules 1, 5 and 6).

CREATE TABLE rule_source (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id             uuid NOT NULL REFERENCES tenant(id),
  jurisdiction_id       uuid NOT NULL REFERENCES jurisdiction(id),
  license_type          text NOT NULL CHECK (length(btrim(license_type)) > 0 AND length(license_type) <= 100),
  label                 text NOT NULL CHECK (length(btrim(label)) > 0 AND length(label) <= 150),
  url                   text NOT NULL CHECK (url ~ '^https?://[^[:space:]]+$' AND length(url) <= 2000),
  check_interval_hours  integer NOT NULL CHECK (check_interval_hours >= 1 AND check_interval_hours <= 720),
  active                boolean NOT NULL DEFAULT true,
  last_checked_at       timestamptz,
  last_content_hash     text CHECK (last_content_hash IS NULL OR last_content_hash ~ '^[0-9a-f]{64}$'),
  created_at            timestamptz NOT NULL DEFAULT now(),
  created_by            uuid REFERENCES app_user(id)
);

-- One row per check, whatever its outcome. CHANGED is the only outcome that carries a document and a rule version.
CREATE TABLE rule_source_check (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        uuid NOT NULL REFERENCES tenant(id),
  rule_source_id   uuid NOT NULL REFERENCES rule_source(id),
  checked_at       timestamptz NOT NULL DEFAULT now(),
  outcome          text NOT NULL CHECK (outcome IN ('UNCHANGED', 'CHANGED', 'DRAFT_EXISTS', 'FETCH_FAILED')),
  http_status      integer CHECK (http_status IS NULL OR (http_status >= 100 AND http_status <= 599)),
  content_hash     text CHECK (content_hash IS NULL OR content_hash ~ '^[0-9a-f]{64}$'),
  detail           text CHECK (detail IS NULL OR length(detail) <= 2000),
  document_id      uuid REFERENCES document(id),
  rule_version_id  uuid REFERENCES rule_version(id),
  CONSTRAINT rule_source_check_changed_has_both
    CHECK ((outcome = 'CHANGED') = (document_id IS NOT NULL AND rule_version_id IS NOT NULL)),
  CONSTRAINT rule_source_check_only_changed_has_either
    CHECK (outcome = 'CHANGED' OR (document_id IS NULL AND rule_version_id IS NULL))
);
CREATE INDEX rule_source_check_latest_idx ON rule_source_check (rule_source_id, checked_at DESC);

-- Same row-level security as every tenant table (V002).
ALTER TABLE rule_source ENABLE ROW LEVEL SECURITY;
ALTER TABLE rule_source FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON rule_source
  USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());
CREATE INDEX rule_source_tenant_idx ON rule_source (tenant_id);

ALTER TABLE rule_source_check ENABLE ROW LEVEL SECURITY;
ALTER TABLE rule_source_check FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON rule_source_check
  USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());
CREATE INDEX rule_source_check_tenant_idx ON rule_source_check (tenant_id);

-- The scheduler looks for due sources across tenants and queues one job each, under the same transaction-local
-- worker flag the job poller uses (D-013). The flag opens exactly two more doors: reading rule_source and inserting
-- into job. Nothing else honours it.
CREATE POLICY worker_due_select ON rule_source FOR SELECT
  USING (current_setting('lexvay.worker', true) = 'on');
CREATE POLICY worker_enqueue_insert ON job FOR INSERT
  WITH CHECK (current_setting('lexvay.worker', true) = 'on');

-- Product rule 5: a check is never changed, a source keeps its history and only changes how it is monitored.
CREATE TRIGGER rule_source_check_immutable BEFORE UPDATE OR DELETE ON rule_source_check
  FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER rule_source_no_delete BEFORE DELETE ON rule_source FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER rule_source_monitoring_only BEFORE UPDATE ON rule_source
  FOR EACH ROW EXECUTE FUNCTION restrict_update_to_columns('active', 'last_checked_at', 'last_content_hash');
