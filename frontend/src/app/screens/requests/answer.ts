import { EvidenceResponseBody } from '../../core/models';
import { PassageSelection } from '../../shared/document-viewer/page-text';

/** The comment limit of POST /mappings/{id}/evidence-responses. */
export const COMMENT_MAX = 4000;

/**
 * The request body for a chosen passage. The quote is the stored page text between the selected offsets
 * (DocumentViewer takes it from the page, not the DOM); the server verifies it again. A blank comment is
 * sent as null.
 */
export function responseBody(documentId: string, selection: PassageSelection, comment: string): EvidenceResponseBody {
  const trimmed = comment.trim();
  return { documentId, pageNumber: selection.pageNumber, quote: selection.quote, comment: trimmed === '' ? null : trimmed };
}
