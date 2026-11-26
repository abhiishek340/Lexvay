-- Requirement-extraction calls belong to a rule version, not to an assessment (D-047).
ALTER TABLE llm_call ADD COLUMN rule_version_id uuid REFERENCES rule_version(id);
CREATE INDEX llm_call_rule_version_idx ON llm_call (rule_version_id);
