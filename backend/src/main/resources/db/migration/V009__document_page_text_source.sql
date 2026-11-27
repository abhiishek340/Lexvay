-- Where a page's text came from. A page read by OCR carries the engine's mean word confidence (0-100) so that
-- evidence cited from it can be treated cautiously (product rule 4). Existing pages came from a text layer.
-- document_page stays immutable (V003): the new columns are filled on insert and never updated.

ALTER TABLE document_page
  ADD COLUMN text_source    text NOT NULL DEFAULT 'TEXT_LAYER' CHECK (text_source IN ('TEXT_LAYER', 'OCR')),
  ADD COLUMN ocr_confidence numeric(5,2) CHECK (ocr_confidence IS NULL OR (ocr_confidence >= 0 AND ocr_confidence <= 100)),
  ADD CONSTRAINT document_page_ocr_confidence_iff_ocr CHECK ((text_source = 'OCR') = (ocr_confidence IS NOT NULL));
