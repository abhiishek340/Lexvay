-- OCR checkpoints (FIX-6). Each scanned page's OCR result is committed in its own transaction, so a failure on a
-- later page keeps the earlier pages' results and the retry of the ingest job reads them back instead of running
-- the engine again. A row records how a page's text was obtained and stays after ingest (product rule 5).
-- Whether the text is enough to count as read is decided in code from this text, every time. The primary key
-- serves the one read (all checkpoints of a document), so there is no other index.

CREATE TABLE document_ocr_page (
  tenant_id        uuid NOT NULL REFERENCES tenant(id),
  document_id      uuid NOT NULL REFERENCES document(id),
  page_number      integer NOT NULL CHECK (page_number > 0),
  text             text NOT NULL,
  mean_confidence  numeric(5,2),
  render_dpi       integer NOT NULL CHECK (render_dpi > 0),
  created_at       timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (document_id, page_number)
);

-- Same row-level security as every tenant table (V002).
ALTER TABLE document_ocr_page ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_ocr_page FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_ocr_page
  USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());

-- Product rule 5: a checkpoint is never changed or removed.
CREATE TRIGGER document_ocr_page_immutable BEFORE UPDATE OR DELETE ON document_ocr_page
  FOR EACH ROW EXECUTE FUNCTION forbid_change();
