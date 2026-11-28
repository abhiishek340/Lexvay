-- Academic pathway model (AC-1). A program's graduation paths are groups of courses: some are all required, some are
-- "choose n" or "choose credits" (D-586). Credits belong to a course version, because a new value is a new version.
-- A pathway group is never edited; a correction retires the group and creates another (product rule 5).
-- Migration numbers follow merge order: V025 (RG-1) merged first, so this is V026.

-- NULL credits means unknown: nothing is ever invented. There is no conversion between units (D-587).
ALTER TABLE course_version ADD COLUMN credits numeric(5,2) CHECK (credits > 0);
ALTER TABLE course_version ADD COLUMN credit_unit text NOT NULL DEFAULT 'SEMESTER' CHECK (credit_unit IN ('SEMESTER', 'QUARTER'));

-- The credits of a pathway's members are read from the newest version of each course.
CREATE INDEX course_version_course_idx ON course_version (course_id, created_at DESC);

CREATE TABLE pathway_group (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         uuid NOT NULL REFERENCES tenant(id),
  program_id        uuid NOT NULL REFERENCES program(id),
  catalog_year      text NOT NULL CHECK (length(btrim(catalog_year)) > 0),
  label             text NOT NULL CHECK (length(btrim(label)) > 0),
  kind              text NOT NULL CHECK (kind IN ('ALL_OF', 'CHOOSE_COUNT', 'CHOOSE_CREDITS')),
  -- NULL means the minimum is not recorded. A group that asks for nothing is not a group.
  min_count         integer CHECK (min_count > 0),
  min_credits       numeric(7,2) CHECK (min_credits > 0),
  -- The passage of a handbook or degree plan that states the group, verified against stored page text (product rule 2).
  evidence_span_id  uuid REFERENCES evidence_span(id),
  created_at        timestamptz NOT NULL DEFAULT now(),
  created_by        uuid REFERENCES app_user(id),
  CONSTRAINT pathway_group_minimum_matches_kind CHECK (
    (min_count IS NULL OR kind = 'CHOOSE_COUNT') AND (min_credits IS NULL OR kind = 'CHOOSE_CREDITS'))
);

CREATE TABLE pathway_group_course (
  group_id   uuid NOT NULL REFERENCES pathway_group(id),
  course_id  uuid NOT NULL REFERENCES course(id),
  tenant_id  uuid NOT NULL REFERENCES tenant(id),
  PRIMARY KEY (group_id, course_id)
);

-- A group that has a row here is retired. The row is its whole history and is never changed.
CREATE TABLE pathway_group_retirement (
  group_id    uuid PRIMARY KEY REFERENCES pathway_group(id),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  retired_at  timestamptz NOT NULL DEFAULT now(),
  retired_by  uuid REFERENCES app_user(id),
  reason      text NOT NULL CHECK (length(btrim(reason)) > 0)
);

-- The active groups of one program and catalog year (PathwayRepository.GROUPS).
CREATE INDEX pathway_group_program_idx ON pathway_group (program_id, catalog_year);

-- Backfill, per program and catalog year of its course versions: one ALL_OF group of the courses marked required and
-- one CHOOSE_COUNT group of the others, with the minimum unknown. Nothing is assumed about how many electives a
-- graduate takes, so the evaluator answers UNKNOWN for them until a person records the minimum (D-588).
-- Row-level security is enabled below, after the rows exist.
INSERT INTO pathway_group (tenant_id, program_id, catalog_year, label, kind)
SELECT DISTINCT c.tenant_id, c.program_id, cv.catalog_year,
       CASE WHEN cv.required THEN 'Required courses' ELSE 'Electives' END,
       CASE WHEN cv.required THEN 'ALL_OF' ELSE 'CHOOSE_COUNT' END
FROM course c JOIN course_version cv ON cv.course_id = c.id;

INSERT INTO pathway_group_course (group_id, course_id, tenant_id)
SELECT DISTINCT g.id, c.id, c.tenant_id
FROM course c
JOIN course_version cv ON cv.course_id = c.id
JOIN pathway_group g ON g.program_id = c.program_id AND g.catalog_year = cv.catalog_year
                    AND g.kind = CASE WHEN cv.required THEN 'ALL_OF' ELSE 'CHOOSE_COUNT' END;

-- Product rule 7, as for every tenant table (V002).
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['pathway_group', 'pathway_group_course', 'pathway_group_retirement']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant())', t);
    EXECUTE format('CREATE INDEX %I ON %I (tenant_id)', t || '_tenant_idx', t);
  END LOOP;
END $$;

-- Product rule 5: no row of these tables is updated or deleted.
CREATE TRIGGER pathway_group_immutable BEFORE UPDATE OR DELETE ON pathway_group FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER pathway_group_course_immutable BEFORE UPDATE OR DELETE ON pathway_group_course FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER pathway_group_retirement_immutable BEFORE UPDATE OR DELETE ON pathway_group_retirement FOR EACH ROW EXECUTE FUNCTION forbid_change();
