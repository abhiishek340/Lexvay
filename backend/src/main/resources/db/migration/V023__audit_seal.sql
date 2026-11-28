-- Tamper-evident audit trail (SEC-3). Triggers already stop the application from changing audit_log; the seal
-- covers what they cannot: a superuser, or a restore from an altered backup. Each tenant's entries form a hash chain
-- (audit_seal), built after the fact by AuditSealer, never inside the business transaction (D-530).

-- One row per sealed audit entry. seq is the order in which entries were sealed, contiguous from 1 for each tenant;
-- row_hash covers the entry and prev_hash, the row_hash of seq - 1 (64 zeros for the first). Never changed.
CREATE TABLE audit_seal (
  tenant_id  uuid        NOT NULL REFERENCES tenant(id),
  seq        bigint      NOT NULL CHECK (seq > 0),
  audit_id   uuid        PRIMARY KEY REFERENCES audit_log(id),
  prev_hash  char(64)    NOT NULL,
  row_hash   char(64)    NOT NULL,
  sealed_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, seq)
);

-- The sealer's queue: written by the same statement as the audit entry, emptied by the sealer. A queue, not a
-- record, so rows may be deleted. created_at is the entry's own and orders the sealing.
CREATE TABLE audit_unsealed (
  audit_id    uuid        PRIMARY KEY REFERENCES audit_log(id),
  tenant_id   uuid        NOT NULL REFERENCES tenant(id),
  created_at  timestamptz NOT NULL
);
CREATE INDEX audit_unsealed_tenant_idx ON audit_unsealed (tenant_id, created_at, audit_id);

ALTER TABLE audit_seal ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_seal FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_seal
  USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());

ALTER TABLE audit_unsealed ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_unsealed FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_unsealed
  USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());
-- The sealer finds the tenants with queued entries under the transaction-local worker flag (D-013), then seals each
-- one as that tenant. The flag opens this one read and nothing else.
CREATE POLICY worker_queue_select ON audit_unsealed FOR SELECT
  USING (current_setting('lexvay.worker', true) = 'on');

-- Product rule 5: a seal is never changed or removed.
CREATE TRIGGER audit_seal_immutable BEFORE UPDATE OR DELETE ON audit_seal
  FOR EACH ROW EXECUTE FUNCTION forbid_change();

-- Entries written before this migration are sealed by the sealer's first runs. The backfill reads every tenant's
-- audit_log, so for its duration the owner is exempt from row-level security on the two tables it touches (the
-- pattern of V016); both are forced again below, in the same transaction.
ALTER TABLE audit_log NO FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_unsealed NO FORCE ROW LEVEL SECURITY;
INSERT INTO audit_unsealed (audit_id, tenant_id, created_at) SELECT id, tenant_id, created_at FROM audit_log;
ALTER TABLE audit_unsealed FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
