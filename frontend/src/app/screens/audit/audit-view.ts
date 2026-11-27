import { DECISION_LABEL, OUTCOME_LABEL, actionLabel, humanize } from '../../core/labels';
import { AuditEntry, DecisionKind, Outcome, UserSummary } from '../../core/models';

export interface AuditRow {
  id: string;
  at: string;
  action: string;
  actionLabel: string;
  entityType: string;
  entityLabel: string;
  entityId: string | null;
  details: string;
  actor: string;
}

export interface AuditFilter { action: string | null; entityType: string | null }

/** Detail keys worth showing, in reading order, with how to word each value. */
const DETAIL_KEYS: { key: string; label: string; format?: (v: unknown) => string; describe?: (v: unknown) => string }[] = [
  { key: 'jurisdiction', label: 'Jurisdiction' },
  { key: 'requirementRef', label: 'Requirement' },
  // "kind" is the decision on review entries and a document or change kind elsewhere.
  { key: 'kind', label: 'Kind', describe: (v) => (isDecision(v) ? `Decision: ${DECISION_LABEL[v]}` : `Kind: ${humanize(String(v))}`) },
  { key: 'outcome', label: 'Outcome', format: (v) => OUTCOME_LABEL[v as Outcome] ?? humanize(String(v)) },
  { key: 'filename', label: 'File' },
  { key: 'label', label: 'Version' },
  { key: 'name', label: 'Name' },
  { key: 'email', label: 'Email' },
  { key: 'note', label: 'Note' },
];

function isDecision(v: unknown): v is DecisionKind {
  return typeof v === 'string' && v in DECISION_LABEL;
}

export function detailSummary(detail: Record<string, unknown>): string {
  return DETAIL_KEYS
    .filter(({ key }) => detail[key] !== undefined && detail[key] !== null && detail[key] !== '')
    .map(({ key, label, format, describe }) => (describe ? describe(detail[key]) : `${label}: ${format ? format(detail[key]) : String(detail[key])}`))
    .join(' · ');
}

/** Actor ids become names; entries without a user were written by background work. */
export function auditRows(entries: AuditEntry[], users: UserSummary[]): AuditRow[] {
  const names = new Map(users.map((u) => [u.id, u.displayName]));
  return entries.map((e) => ({
    id: e.id,
    at: e.createdAt,
    action: e.action,
    actionLabel: actionLabel(e.action),
    entityType: e.entityType,
    entityLabel: humanize(e.entityType),
    entityId: e.entityId,
    details: detailSummary(e.detail),
    actor: e.userId === null ? 'System' : (names.get(e.userId) ?? 'Former user'),
  }));
}

/** Newest first regardless of the order the server returned. */
export function filterRows(rows: AuditRow[], filter: AuditFilter): AuditRow[] {
  return rows
    .filter((r) => (!filter.action || r.action === filter.action) && (!filter.entityType || r.entityType === filter.entityType))
    .sort((a, b) => b.at.localeCompare(a.at));
}

/** Choices for a filter: each distinct value once, labelled, alphabetically by label. */
export function filterOptions(rows: AuditRow[], pick: (r: AuditRow) => [value: string, label: string]): { value: string; label: string }[] {
  const seen = new Map<string, string>();
  for (const r of rows) {
    const [value, label] = pick(r);
    seen.set(value, label);
  }
  return [...seen].map(([value, label]) => ({ value, label: label.charAt(0).toUpperCase() + label.slice(1) }))
    .sort((a, b) => a.label.localeCompare(b.label));
}
