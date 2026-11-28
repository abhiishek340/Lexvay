-- Data mode and spend gate (MODE-1, D-560 to D-565).
--
-- A tenant is either DEMO (fictional data, the scripted model allowed, every export marked) or LIVE (real data, never
-- the scripted model). A tenant is LIVE unless the code that creates it says DEMO; the seed does. The mode is changed
-- only by a database administrator, never through the API (docs/OPERATIONS.md).
--
-- Tenants that exist today are the fictional seed tenants and the tenants of tests and evaluations: nothing is
-- deployed and no real data exists (D-453). Adding the column with the DEMO default marks them without an UPDATE,
-- which row-level security would not let a migration run across tenants; the default then becomes LIVE.
ALTER TABLE tenant ADD COLUMN data_mode text NOT NULL DEFAULT 'DEMO' CHECK (data_mode IN ('DEMO', 'LIVE'));
ALTER TABLE tenant ALTER COLUMN data_mode SET DEFAULT 'LIVE';

-- A change of mode is the most consequential administrative act, so the database audits it itself (product rule 9):
-- it cannot depend on the person at the SQL prompt remembering to. The row is written for the tenant whose mode
-- changed, with the database role that made the change.
CREATE FUNCTION audit_tenant_data_mode() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO audit_log (tenant_id, user_id, action, entity_type, entity_id, detail)
  VALUES (NEW.id, NULL, 'TENANT_DATA_MODE_CHANGED', 'tenant', NEW.id,
          jsonb_build_object('from', OLD.data_mode, 'to', NEW.data_mode, 'databaseRole', current_user));
  RETURN NEW;
END $$;

CREATE TRIGGER tenant_data_mode_audited AFTER UPDATE OF data_mode ON tenant
  FOR EACH ROW WHEN (OLD.data_mode IS DISTINCT FROM NEW.data_mode) EXECUTE FUNCTION audit_tenant_data_mode();

-- The spend gate adds up what was recorded this month across every tenant, because the budget belongs to the
-- deployment's one provider account. Like the job worker's flag (D-013), the transaction-local flag is honoured by
-- one policy only, on SELECT, and the gate reads totals, never the stored input or output.
CREATE POLICY spend_gate_select ON llm_call FOR SELECT
  USING (current_setting('lexvay.spend_gate', true) = 'on');

-- The gate's one query: the calls since the start of the month.
CREATE INDEX llm_call_created_idx ON llm_call (created_at);
