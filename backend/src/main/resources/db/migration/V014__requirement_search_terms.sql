-- Search terms of a requirement (F7c). The extraction proposes the vocabulary a syllabus would use for a
-- requirement's subject, a reviewer corrects it before approval, and retrieval searches for it together with
-- the requirement's own words. A term is a hint for retrieval; it never produces evidence by itself.
--
-- At most 12 terms, each 1 to 40 characters after trimming. The rule is a function so that the constraint
-- stays one readable line and a test can call the function directly.
CREATE FUNCTION search_terms_valid(terms text[]) RETURNS boolean
LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$
  SELECT coalesce(cardinality(terms) <= 12, false)
     AND NOT EXISTS (SELECT 1 FROM unnest(terms) AS t WHERE t IS NULL OR length(btrim(t)) NOT BETWEEN 1 AND 40)
$$;

-- No existing row is updated: the default fills the new column without firing the approval lock, and the
-- constraint is checked against the stored rows without the row-level security the owner is bound by. Unlike
-- V012 this migration therefore leaves the lock trigger and FORCE ROW LEVEL SECURITY alone. Approved rule
-- versions keep the empty list they have; their requirement rows stay immutable.
ALTER TABLE requirement
  ADD COLUMN search_terms text[] NOT NULL DEFAULT '{}',
  ADD CONSTRAINT requirement_search_terms_valid CHECK (search_terms_valid(search_terms));
