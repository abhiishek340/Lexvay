-- User administration (F5a). An administrator adds colleagues, changes their role and removes their access.
-- Users are never deleted (product rule 9: every audit entry names who acted), so access is withdrawn with a flag.
-- Updates to app_user stay ordinary: there is no immutability trigger on it and none is added here.

-- active: a deactivated user cannot sign in and an open session ends within seconds (D-374).
-- security_stamp: counts changes to what the user may do (role, access, password); a session remembers the value it
-- was issued with and is refused when the current value differs (D-374).
ALTER TABLE app_user
  ADD COLUMN active          boolean NOT NULL DEFAULT true,
  ADD COLUMN security_stamp  integer NOT NULL DEFAULT 0;

-- One row per setup code handed to an administrator. The code itself is never stored, only its SHA-256 (D-372).
CREATE TABLE user_setup_token (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  user_id     uuid NOT NULL REFERENCES app_user(id),
  token_hash  text NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
  expires_at  timestamptz NOT NULL,
  used_at     timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now(),
  created_by  uuid REFERENCES app_user(id),
  CONSTRAINT user_setup_token_expires_after_creation CHECK (expires_at > created_at)
);

-- Same row-level security as every tenant table (V002).
ALTER TABLE user_setup_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_setup_token FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON user_setup_token
  USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());
CREATE INDEX user_setup_token_tenant_idx ON user_setup_token (tenant_id);

-- Setting a password happens before anybody is signed in, so the tenant is not known when the code is looked up.
-- Exactly as for app_user (D-034), a transaction-local flag that only that lookup sets opens SELECT, and nothing else.
CREATE POLICY auth_lookup ON user_setup_token FOR SELECT
  USING (current_setting('lexvay.auth_lookup', true) = 'on');

-- Issuing a new code closes the user's open ones.
CREATE INDEX user_setup_token_open_idx ON user_setup_token (user_id) WHERE used_at IS NULL;

-- Product rule 5: a code is never edited or removed; the only change is the moment it was used.
CREATE TRIGGER user_setup_token_no_delete BEFORE DELETE ON user_setup_token FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER user_setup_token_used_only BEFORE UPDATE ON user_setup_token
  FOR EACH ROW EXECUTE FUNCTION restrict_update_to_columns('used_at');
