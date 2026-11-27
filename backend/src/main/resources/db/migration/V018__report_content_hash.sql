-- Report content hash (F14). Signed evidence reports leave Lexvay as PDF files; a file someone hands back is checked
-- by looking up the SHA-256 of its bytes. The job that generates a report sets the hash of the exact bytes it stores,
-- in the same update that marks the report ready (ReportRepository.markReady). The file that is checked is never
-- stored.
--
-- Reports generated before this migration keep a null hash, so a check finds no match for them. Nothing is deployed
-- and the seed generates its reports anew, so there is no backfill (D-453).
ALTER TABLE report ADD COLUMN content_sha256 text CHECK (content_sha256 ~ '^[0-9a-f]{64}$');

-- The check's one lookup, within the tenant: row-level security adds tenant_id = current_tenant(), which is the
-- leading index condition (ReportRepository.CHECK, IndexUsageTest).
CREATE INDEX report_content_sha_idx ON report (tenant_id, content_sha256) WHERE content_sha256 IS NOT NULL;
