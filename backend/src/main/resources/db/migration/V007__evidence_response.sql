-- A person's answer to an EVIDENCE_REQUESTED review event: a verified passage of a program document.
-- It never changes the mapping's proposed finding (product rules 1 and 5); the reviewer decides afterwards.
-- At most one response per request: the response reopens the mapping, so a second answer needs a new request.

CREATE TABLE evidence_response (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         uuid NOT NULL REFERENCES tenant(id),
  mapping_id        uuid NOT NULL REFERENCES mapping(id),
  review_event_id   uuid NOT NULL UNIQUE REFERENCES review_event(id),
  evidence_span_id  uuid NOT NULL REFERENCES evidence_span(id),
  comment           text CHECK (comment IS NULL OR length(comment) <= 4000),
  created_at        timestamptz NOT NULL DEFAULT now(),
  created_by        uuid NOT NULL REFERENCES app_user(id)
);
CREATE INDEX evidence_response_mapping_idx ON evidence_response (mapping_id, created_at DESC);

-- Same row-level security as every tenant table (V002) and same immutability as the other event tables (V003).
ALTER TABLE evidence_response ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidence_response FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON evidence_response
  USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());
CREATE INDEX evidence_response_tenant_idx ON evidence_response (tenant_id);

CREATE TRIGGER evidence_response_immutable BEFORE UPDATE OR DELETE ON evidence_response
  FOR EACH ROW EXECUTE FUNCTION forbid_change();
