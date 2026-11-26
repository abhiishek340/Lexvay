-- Product rules 5 and 6: nothing is overwritten. Immutable tables reject every UPDATE and DELETE.
-- A few tables allow exactly one controlled change (D-012, D-035). SQLSTATE LX001 marks these errors.

CREATE FUNCTION forbid_change() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'Lexvay: % on % is not allowed, rows in this table are immutable', TG_OP, TG_TABLE_NAME
    USING ERRCODE = 'LX001';
END $$;

-- Allows an UPDATE that changes only the columns named as trigger arguments.
CREATE FUNCTION restrict_update_to_columns() RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE allowed text[] := TG_ARGV;
BEGIN
  IF (to_jsonb(NEW) - allowed) <> (to_jsonb(OLD) - allowed) THEN
    RAISE EXCEPTION 'Lexvay: only % may change on %', array_to_string(allowed, ', '), TG_TABLE_NAME
      USING ERRCODE = 'LX001';
  END IF;
  RETURN NEW;
END $$;

-- rule_version: the only permitted change is approval, once, from unapproved to approved.
CREATE FUNCTION rule_version_approval_only() RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE approval text[] := ARRAY['requirements_approved_by', 'requirements_approved_at'];
BEGIN
  IF (to_jsonb(NEW) - approval) <> (to_jsonb(OLD) - approval) THEN
    RAISE EXCEPTION 'Lexvay: only the approval columns may change on rule_version' USING ERRCODE = 'LX001';
  END IF;
  IF OLD.requirements_approved_at IS NOT NULL THEN
    RAISE EXCEPTION 'Lexvay: rule_version is already approved' USING ERRCODE = 'LX001';
  END IF;
  IF NEW.requirements_approved_at IS NULL OR NEW.requirements_approved_by IS NULL THEN
    RAISE EXCEPTION 'Lexvay: approval cannot be removed from rule_version' USING ERRCODE = 'LX001';
  END IF;
  RETURN NEW;
END $$;

-- requirement: editable (replaceable) until its rule version is approved, immutable afterwards.
CREATE FUNCTION requirement_locked_after_approval() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM rule_version rv WHERE rv.id = OLD.rule_version_id AND rv.requirements_approved_at IS NOT NULL) THEN
    RAISE EXCEPTION 'Lexvay: requirements of an approved rule version are immutable' USING ERRCODE = 'LX001';
  END IF;
  IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER document_immutable BEFORE UPDATE OR DELETE ON document FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER document_page_immutable BEFORE UPDATE OR DELETE ON document_page FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER course_version_immutable BEFORE UPDATE OR DELETE ON course_version FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER evidence_span_immutable BEFORE UPDATE OR DELETE ON evidence_span FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER program_fact_immutable BEFORE UPDATE OR DELETE ON program_fact FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER review_event_immutable BEFORE UPDATE OR DELETE ON review_event FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER audit_log_immutable BEFORE UPDATE OR DELETE ON audit_log FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER llm_call_immutable BEFORE UPDATE OR DELETE ON llm_call FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER determination_course_version_immutable BEFORE UPDATE OR DELETE ON determination_course_version FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TRIGGER determination_no_delete BEFORE DELETE ON determination FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER determination_status_only BEFORE UPDATE ON determination FOR EACH ROW EXECUTE FUNCTION restrict_update_to_columns('status');

CREATE TRIGGER change_event_no_delete BEFORE DELETE ON change_event FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER change_event_status_only BEFORE UPDATE ON change_event FOR EACH ROW EXECUTE FUNCTION restrict_update_to_columns('status');

CREATE TRIGGER rule_version_no_delete BEFORE DELETE ON rule_version FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER rule_version_approval BEFORE UPDATE ON rule_version FOR EACH ROW EXECUTE FUNCTION rule_version_approval_only();

CREATE TRIGGER requirement_locked BEFORE UPDATE OR DELETE ON requirement FOR EACH ROW EXECUTE FUNCTION requirement_locked_after_approval();
