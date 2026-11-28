-- Evidence package (F15). One immutable ZIP per request that holds everything behind a signed determination: the
-- report, the exact rule and course versions, every quoted passage with its source document and the audit trail.
-- A job builds it, so a row starts QUEUED and is finished exactly once, as READY with the stored file's key, hash and
-- size, or as FAILED. (V019 belongs to SEC-3, which was not on main when this was written.)

CREATE TABLE evidence_package (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         uuid NOT NULL REFERENCES tenant(id),
  determination_id  uuid NOT NULL REFERENCES determination(id),
  status            text NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'READY', 'FAILED')),
  storage_key       text CHECK (storage_key IS NULL OR length(storage_key) > 0),
  content_sha256    char(64) CHECK (content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$'),
  size_bytes        bigint CHECK (size_bytes IS NULL OR size_bytes > 0),
  created_at        timestamptz NOT NULL DEFAULT now(),
  created_by        uuid REFERENCES app_user(id),
  -- A package that is ready has its file; one that is not has none (the file is stored whole or not at all).
  CONSTRAINT evidence_package_file_iff_ready CHECK (
    (status = 'READY') = (storage_key IS NOT NULL AND content_sha256 IS NOT NULL AND size_bytes IS NOT NULL)
    AND (status = 'READY' OR (storage_key IS NULL AND content_sha256 IS NULL AND size_bytes IS NULL)))
);

-- The packages of one determination, newest first.
CREATE INDEX evidence_package_determination_idx ON evidence_package (determination_id, created_at DESC);

-- Product rule 7, as for every tenant table (V002).
ALTER TABLE evidence_package ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidence_package FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON evidence_package
  USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());
CREATE INDEX evidence_package_tenant_idx ON evidence_package (tenant_id);

-- Product rule 5: a package is never deleted and is finished once: QUEUED to READY or FAILED, setting only the file
-- columns. Nothing else about the row changes, whatever its status.
CREATE FUNCTION evidence_package_finish_once() RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE finishing text[] := ARRAY['status', 'storage_key', 'content_sha256', 'size_bytes'];
BEGIN
  IF OLD.status <> 'QUEUED' OR NEW.status NOT IN ('READY', 'FAILED') THEN
    RAISE EXCEPTION 'Lexvay: an evidence package can only change from QUEUED to READY or FAILED' USING ERRCODE = 'LX001';
  END IF;
  IF (to_jsonb(NEW) - finishing) <> (to_jsonb(OLD) - finishing) THEN
    RAISE EXCEPTION 'Lexvay: only the file columns and the status may change on evidence_package' USING ERRCODE = 'LX001';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER evidence_package_no_delete BEFORE DELETE ON evidence_package FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER evidence_package_finish BEFORE UPDATE ON evidence_package FOR EACH ROW EXECUTE FUNCTION evidence_package_finish_once();
