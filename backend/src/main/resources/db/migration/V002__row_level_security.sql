-- Product rule 7: tenants are isolated in the database. Every table except jurisdiction gets
-- ENABLE + FORCE ROW LEVEL SECURITY (FORCE binds the owner too) and one policy for all commands.
-- current_tenant() is NULL when the session setting is absent, so an unset tenant sees and writes
-- nothing (fail closed).

CREATE FUNCTION current_tenant() RETURNS uuid
LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('lexvay.tenant_id', true), '')::uuid $$;

DO $$
DECLARE t text;
BEGIN
  FOR t IN
    SELECT table_name FROM information_schema.tables
    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
      AND table_name NOT IN ('jurisdiction', 'flyway_schema_history')
    ORDER BY table_name
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant())', t);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (tenant_id)', t || '_tenant_idx', t);
  END LOOP;
END $$;

-- The job poller claims work across tenants under a transaction-local flag that nothing else honours
-- (D-013). The claimed job's stage then runs under that job's tenant.
CREATE POLICY worker_claim_select ON job FOR SELECT
  USING (current_setting('lexvay.worker', true) = 'on');
CREATE POLICY worker_claim_update ON job FOR UPDATE
  USING (current_setting('lexvay.worker', true) = 'on')
  WITH CHECK (current_setting('lexvay.worker', true) = 'on');

-- Login must find a user by email before the tenant is known (D-034). SELECT only.
CREATE POLICY auth_lookup ON app_user FOR SELECT
  USING (current_setting('lexvay.auth_lookup', true) = 'on');
