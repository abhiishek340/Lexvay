-- EV-2: the evaluation lab's generic baseline asks the model the same kind of question as the matcher but is not the
-- pipeline, so its calls are stored under their own purpose instead of being mislabelled as evidence matching.
ALTER TABLE llm_call DROP CONSTRAINT llm_call_purpose_check;
ALTER TABLE llm_call ADD CONSTRAINT llm_call_purpose_check
  CHECK (purpose IN ('EXTRACT_REQUIREMENTS', 'MATCH_EVIDENCE', 'EVAL_BASELINE'));
