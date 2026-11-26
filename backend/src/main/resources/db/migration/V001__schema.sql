-- Lexvay schema. Every table except jurisdiction carries tenant_id; row-level security is added in V002,
-- immutability triggers in V003. All ids are UUIDs. Nothing here is student data (product rule 8).

CREATE TABLE tenant (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL,
  name        text NOT NULL CHECK (length(btrim(name)) > 0),
  created_at  timestamptz NOT NULL DEFAULT now(),
  created_by  uuid,
  CONSTRAINT tenant_is_its_own_tenant CHECK (tenant_id = id)
);

CREATE TABLE app_user (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     uuid NOT NULL REFERENCES tenant(id),
  email         text NOT NULL UNIQUE CHECK (email = lower(email) AND position('@' in email) > 1),
  display_name  text NOT NULL CHECK (length(btrim(display_name)) > 0),
  role          text NOT NULL CHECK (role IN ('CONTRIBUTOR', 'REVIEWER', 'SIGNER', 'ADMIN')),
  password_hash text,
  created_at    timestamptz NOT NULL DEFAULT now(),
  created_by    uuid
);

-- Reference data shared by all tenants. The only table without tenant_id.
CREATE TABLE jurisdiction (
  id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code  text NOT NULL UNIQUE CHECK (code ~ '^[A-Z]{2}$'),
  name  text NOT NULL UNIQUE
);

CREATE TABLE program (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     uuid NOT NULL REFERENCES tenant(id),
  name          text NOT NULL CHECK (length(btrim(name)) > 0),
  license_type  text NOT NULL CHECK (length(btrim(license_type)) > 0),
  catalog_year  text NOT NULL CHECK (length(btrim(catalog_year)) > 0),
  created_at    timestamptz NOT NULL DEFAULT now(),
  created_by    uuid
);

CREATE TABLE program_jurisdiction (
  program_id       uuid NOT NULL REFERENCES program(id),
  jurisdiction_id  uuid NOT NULL REFERENCES jurisdiction(id),
  tenant_id        uuid NOT NULL REFERENCES tenant(id),
  created_at       timestamptz NOT NULL DEFAULT now(),
  created_by       uuid,
  PRIMARY KEY (program_id, jurisdiction_id)
);

CREATE TABLE course (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  program_id  uuid NOT NULL REFERENCES program(id),
  code        text NOT NULL CHECK (length(btrim(code)) > 0),
  title       text NOT NULL CHECK (length(btrim(title)) > 0),
  created_at  timestamptz NOT NULL DEFAULT now(),
  created_by  uuid,
  UNIQUE (program_id, code)
);

CREATE TABLE course_version (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      uuid NOT NULL REFERENCES tenant(id),
  course_id      uuid NOT NULL REFERENCES course(id),
  catalog_year   text NOT NULL CHECK (length(btrim(catalog_year)) > 0),
  required       boolean NOT NULL,
  supersedes_id  uuid UNIQUE REFERENCES course_version(id),
  created_at     timestamptz NOT NULL DEFAULT now(),
  created_by     uuid
);

CREATE TABLE document (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id          uuid NOT NULL REFERENCES tenant(id),
  program_id         uuid REFERENCES program(id),
  course_version_id  uuid REFERENCES course_version(id),
  filename           text NOT NULL CHECK (length(btrim(filename)) > 0),
  sha256             text NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  storage_key        text NOT NULL CHECK (length(storage_key) > 0),
  kind               text NOT NULL CHECK (kind IN ('SYLLABUS', 'DEGREE_PLAN', 'HANDBOOK', 'ACCREDITATION', 'RULE_TEXT')),
  created_at         timestamptz NOT NULL DEFAULT now(),
  created_by         uuid,
  -- Rule text belongs to no program (D-009); everything else belongs to exactly one.
  CONSTRAINT document_kind_matches_program CHECK ((kind = 'RULE_TEXT') = (program_id IS NULL)),
  CONSTRAINT document_course_needs_program CHECK (course_version_id IS NULL OR program_id IS NOT NULL),
  -- Duplicate upload detection by content hash, per tenant and program.
  UNIQUE NULLS NOT DISTINCT (tenant_id, program_id, sha256)
);

CREATE TABLE document_page (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    uuid NOT NULL REFERENCES tenant(id),
  document_id  uuid NOT NULL REFERENCES document(id),
  page_number  integer NOT NULL CHECK (page_number >= 1),
  text         text NOT NULL,
  char_start   integer NOT NULL CHECK (char_start >= 0),
  char_end     integer NOT NULL,
  tsv          tsvector GENERATED ALWAYS AS (to_tsvector('english', text)) STORED,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  UNIQUE (document_id, page_number),
  CONSTRAINT document_page_offsets CHECK (char_end >= char_start)
);
CREATE INDEX document_page_tsv_idx ON document_page USING GIN (tsv);

CREATE TABLE rule_version (
  id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id                 uuid NOT NULL REFERENCES tenant(id),
  jurisdiction_id           uuid NOT NULL REFERENCES jurisdiction(id),
  license_type              text NOT NULL CHECK (length(btrim(license_type)) > 0),
  label                     text NOT NULL CHECK (length(btrim(label)) > 0),
  source_url                text NOT NULL,
  effective_from            date NOT NULL,
  supersedes_id             uuid UNIQUE REFERENCES rule_version(id),
  document_id               uuid NOT NULL REFERENCES document(id),
  requirements_approved_by  uuid REFERENCES app_user(id),
  requirements_approved_at  timestamptz,
  created_at                timestamptz NOT NULL DEFAULT now(),
  created_by                uuid,
  CONSTRAINT rule_version_approval_pair CHECK ((requirements_approved_by IS NULL) = (requirements_approved_at IS NULL))
);

CREATE TABLE requirement (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         uuid NOT NULL REFERENCES tenant(id),
  rule_version_id   uuid NOT NULL REFERENCES rule_version(id),
  ref               text NOT NULL CHECK (length(btrim(ref)) > 0),
  title             text NOT NULL CHECK (length(btrim(title)) > 0),
  text              text NOT NULL CHECK (length(btrim(text)) > 0),
  type              text NOT NULL CHECK (type IN ('NUMERIC', 'SUBJECT', 'SUPERVISED_HOURS', 'ACCREDITATION', 'JURISDICTION_SPECIFIC')),
  numeric_field     text CHECK (numeric_field IS NULL OR numeric_field IN ('TOTAL_CREDITS', 'PRACTICUM_HOURS', 'INTERNSHIP_HOURS')),
  numeric_operator  text CHECK (numeric_operator IS NULL OR numeric_operator IN ('>=', '<=', '=', '>', '<')),
  numeric_value     numeric,
  created_at        timestamptz NOT NULL DEFAULT now(),
  created_by        uuid,
  UNIQUE (rule_version_id, ref),
  -- Numeric types carry all three numeric fields; other types carry none (product rule 3).
  CONSTRAINT requirement_numeric_fields CHECK (
    (type IN ('NUMERIC', 'SUPERVISED_HOURS') AND numeric_field IS NOT NULL AND numeric_operator IS NOT NULL AND numeric_value IS NOT NULL)
    OR
    (type NOT IN ('NUMERIC', 'SUPERVISED_HOURS') AND numeric_field IS NULL AND numeric_operator IS NULL AND numeric_value IS NULL)
  )
);

CREATE TABLE evidence_span (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    uuid NOT NULL REFERENCES tenant(id),
  document_id  uuid NOT NULL REFERENCES document(id),
  page_number  integer NOT NULL CHECK (page_number >= 1),
  char_start   integer NOT NULL CHECK (char_start >= 0),
  char_end     integer NOT NULL,
  quote        text NOT NULL CHECK (length(btrim(quote)) > 0),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  CONSTRAINT evidence_span_offsets CHECK (char_end > char_start)
);

CREATE TABLE program_fact (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         uuid NOT NULL REFERENCES tenant(id),
  program_id        uuid NOT NULL REFERENCES program(id),
  key               text NOT NULL CHECK (key IN ('TOTAL_CREDITS', 'PRACTICUM_HOURS', 'INTERNSHIP_HOURS')),
  value             numeric NOT NULL,
  evidence_span_id  uuid NOT NULL REFERENCES evidence_span(id),
  created_at        timestamptz NOT NULL DEFAULT now(),
  created_by        uuid
);

CREATE TABLE assessment (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       uuid NOT NULL REFERENCES tenant(id),
  program_id      uuid NOT NULL REFERENCES program(id),
  status          text NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'FINISHED', 'FAILED')),
  started_at      timestamptz,
  finished_at     timestamptz,
  llm_model       text NOT NULL,
  prompt_version  text NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  created_by      uuid
);

CREATE TABLE mapping (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         uuid NOT NULL REFERENCES tenant(id),
  assessment_id     uuid NOT NULL REFERENCES assessment(id),
  requirement_id    uuid NOT NULL REFERENCES requirement(id),
  jurisdiction_id   uuid NOT NULL REFERENCES jurisdiction(id),
  -- Product rule 1: the system proposes only these three values. A confirmed gap is a review event.
  finding           text NOT NULL CHECK (finding IN ('EVIDENCE_FOUND', 'EVIDENCE_MISSING', 'AMBIGUOUS')),
  method            text NOT NULL CHECK (method IN ('RULES_ENGINE', 'MATCH')),
  rationale         text NOT NULL CHECK (length(btrim(rationale)) > 0),
  evidence_span_id  uuid REFERENCES evidence_span(id),
  stale             boolean NOT NULL DEFAULT false,
  created_at        timestamptz NOT NULL DEFAULT now(),
  created_by        uuid,
  UNIQUE (assessment_id, requirement_id),
  -- Product rule 2: no evidence without a verified quote.
  CONSTRAINT mapping_evidence_requires_span CHECK (finding = 'EVIDENCE_MISSING' OR evidence_span_id IS NOT NULL)
);

CREATE TABLE review_event (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  mapping_id  uuid NOT NULL REFERENCES mapping(id),
  kind        text NOT NULL CHECK (kind IN ('ACCEPTED', 'EVIDENCE_REQUESTED', 'GAP_CONFIRMED', 'REOPENED')),
  note        text,
  user_id     uuid REFERENCES app_user(id),
  created_at  timestamptz NOT NULL DEFAULT now(),
  created_by  uuid,
  CONSTRAINT gap_confirmed_requires_note CHECK (kind <> 'GAP_CONFIRMED' OR length(btrim(coalesce(note, ''))) > 0)
);
CREATE INDEX review_event_mapping_idx ON review_event (mapping_id, created_at DESC);

CREATE TABLE determination (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        uuid NOT NULL REFERENCES tenant(id),
  program_id       uuid NOT NULL REFERENCES program(id),
  jurisdiction_id  uuid NOT NULL REFERENCES jurisdiction(id),
  rule_version_id  uuid NOT NULL REFERENCES rule_version(id),
  assessment_id    uuid NOT NULL REFERENCES assessment(id),
  outcome          text NOT NULL CHECK (outcome IN ('MEETS', 'DOES_NOT_MEET', 'NOT_DETERMINED')),
  signed_by        uuid NOT NULL REFERENCES app_user(id),
  signed_at        timestamptz NOT NULL DEFAULT now(),
  status           text NOT NULL DEFAULT 'SIGNED' CHECK (status IN ('SIGNED', 'REOPENED')),
  versions_hash    text NOT NULL CHECK (versions_hash ~ '^[0-9a-f]{64}$'),
  created_at       timestamptz NOT NULL DEFAULT now(),
  created_by       uuid
);

CREATE TABLE determination_course_version (
  determination_id   uuid NOT NULL REFERENCES determination(id),
  course_version_id  uuid NOT NULL REFERENCES course_version(id),
  tenant_id          uuid NOT NULL REFERENCES tenant(id),
  created_at         timestamptz NOT NULL DEFAULT now(),
  created_by         uuid,
  PRIMARY KEY (determination_id, course_version_id)
);

CREATE TABLE change_event (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  kind        text NOT NULL CHECK (kind IN ('RULE_VERSION', 'COURSE_VERSION')),
  old_id      uuid NOT NULL,
  new_id      uuid NOT NULL,
  status      text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'HANDLED')),
  created_at  timestamptz NOT NULL DEFAULT now(),
  created_by  uuid
);

CREATE TABLE audit_log (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    uuid NOT NULL REFERENCES tenant(id),
  user_id      uuid REFERENCES app_user(id),
  action       text NOT NULL CHECK (length(btrim(action)) > 0),
  entity_type  text NOT NULL CHECK (length(btrim(entity_type)) > 0),
  entity_id    uuid,
  detail       jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid
);
CREATE INDEX audit_log_newest_idx ON audit_log (tenant_id, created_at DESC);

CREATE TABLE job (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  type        text NOT NULL CHECK (length(btrim(type)) > 0),
  payload     jsonb NOT NULL DEFAULT '{}'::jsonb,
  status      text NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
  attempts    integer NOT NULL DEFAULT 0,
  locked_at   timestamptz,
  error       text,
  created_at  timestamptz NOT NULL DEFAULT now(),
  created_by  uuid
);
CREATE INDEX job_claim_idx ON job (status, created_at);

-- Raw model input and output for every proposal (D-014).
CREATE TABLE llm_call (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       uuid NOT NULL REFERENCES tenant(id),
  assessment_id   uuid REFERENCES assessment(id),
  requirement_id  uuid REFERENCES requirement(id),
  purpose         text NOT NULL CHECK (purpose IN ('EXTRACT_REQUIREMENTS', 'MATCH_EVIDENCE')),
  model           text NOT NULL,
  prompt_version  text NOT NULL,
  request         jsonb NOT NULL,
  response        text,
  input_tokens    integer,
  output_tokens   integer,
  duration_ms     integer,
  created_at      timestamptz NOT NULL DEFAULT now(),
  created_by      uuid
);

CREATE TABLE report (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         uuid NOT NULL REFERENCES tenant(id),
  determination_id  uuid NOT NULL REFERENCES determination(id),
  storage_key       text,
  status            text NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'READY', 'FAILED')),
  created_at        timestamptz NOT NULL DEFAULT now(),
  created_by        uuid
);
