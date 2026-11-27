-- GET /assessments/{id}/usage aggregates the model calls of one assessment (F6, D-145).
CREATE INDEX llm_call_assessment_idx ON llm_call (assessment_id);
