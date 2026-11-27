import { humanize } from '../../core/labels';
import { EvidenceResponseBody, ProgramDocument } from '../../core/models';
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

export interface DocumentOption {
  id: string;
  /** The Select's accessible option text: file name, kind and, for an earlier catalog year, that year. */
  label: string;
  filename: string;
  kind: string;
  /** Shown only for a document of an earlier catalog year. */
  catalogYear: string | null;
  disabled: boolean;
}
export interface DocumentGroup { label: string; items: DocumentOption[] }

export const PROGRAM_DOCUMENTS = 'Program documents';
export const NO_TEXT_YET = 'No text available yet';

/**
 * The picker's groups: program-level documents first, then one group per course in the server's order (course
 * code, then the newest catalog year). Rule text never appears: it belongs to no program. Earlier catalog years
 * are left out unless asked for. A document without pages cannot be quoted, so it is listed but disabled.
 */
export function documentGroups(documents: readonly ProgramDocument[], includeEarlier: boolean): DocumentGroup[] {
  const groups = new Map<string, DocumentGroup>();
  for (const d of documents) {
    if (!d.current && !includeEarlier) { continue; }
    const key = d.courseCode ?? '';
    const label = d.courseCode ? (d.courseTitle ? `${d.courseCode} ${d.courseTitle}` : d.courseCode) : PROGRAM_DOCUMENTS;
    const group = groups.get(key) ?? { label, items: [] };
    groups.set(key, group);
    const kind = humanize(d.kind);
    const catalogYear = d.current ? null : d.catalogYear;
    const parts = [d.filename, kind, catalogYear ? `catalog year ${catalogYear}` : null, d.pageCount === 0 ? NO_TEXT_YET : null];
    group.items.push({ id: d.id, label: parts.filter((p) => p !== null).join(', '), filename: d.filename, kind, catalogYear, disabled: d.pageCount === 0 });
  }
  const program = groups.get('');
  groups.delete('');
  return program ? [program, ...groups.values()] : [...groups.values()];
}

/** Whether the switch for earlier catalog years has anything to add. */
export function hasEarlierDocuments(documents: readonly ProgramDocument[]): boolean {
  return documents.some((d) => !d.current);
}
