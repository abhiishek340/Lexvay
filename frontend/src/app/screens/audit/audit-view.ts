import { DECISION_LABEL, NUMERIC_FIELD_LABEL, OUTCOME_LABEL, ROLE_LABEL, humanize } from '../../core/labels';
import { AuditEntry, AuditVerification, DecisionKind, NumericField, Outcome, Role, UserSummary } from '../../core/models';
import { ACTION_LABEL, auditSentence, capitalize } from './audit-sentence';

/** One detail of an entry, worded: "Decision: Gap confirmed". `mono` for ids and hashes. */
export interface DetailRow { label: string; value: string; mono: boolean }

export interface AuditRow {
  id: string;
  at: string;
  action: string;
  /** Who acted: a person of the tenant by name, "System" for background work, "Former user" for an id the tenant no longer lists. */
  actor: string;
  sentence: string;
  /** The sentence after the actor's name, so the name can be set in bold: " accepted CA-3". */
  rest: string;
  recordLabel: string;
  entityId: string | null;
  details: DetailRow[];
}

/** The record types the backend writes, in plain words. */
export const RECORD_TYPE_LABEL: Record<string, string> = {
  app_user: 'User',
  assessment: 'Assessment',
  audit_log: 'Audit trail',
  change_event: 'Change',
  course: 'Course',
  determination: 'Determination',
  determination_register: 'Determination register',
  document: 'Document',
  job: 'Background job',
  mapping: 'Requirement finding',
  program: 'Program',
  program_fact: 'Program fact',
  report: 'Evidence report',
  rule_source: 'Rule source',
  rule_version: 'Rule version',
  tenant: 'Organization',
};

export function recordLabel(entityType: string): string {
  return RECORD_TYPE_LABEL[entityType] ?? capitalize(humanize(entityType));
}

const yesNo = (v: unknown): string => (v === true ? 'Yes' : v === false ? 'No' : String(v));
const roleOrValue = (v: unknown): string => ROLE_LABEL[v as Role] ?? String(v);
const listOf = (noun: string) => (v: unknown): string => (Array.isArray(v) ? `${v.length} ${noun}${v.length === 1 ? '' : 's'}` : String(v));

interface DetailKey { label: string; format?: (value: unknown, names: (code: string) => string) => string }

/**
 * The detail keys the backend writes, in reading order, each with its words. A key not listed here still shows, with
 * its name in words, when its value is plain text, a number, a yes or no, or a list of those; nested objects never
 * show (they are in the CSV for administrators).
 */
const DETAIL_KEYS: Record<string, DetailKey> = {
  jurisdiction: { label: 'Jurisdiction', format: (v, names) => `${names(String(v))} (${String(v)})` },
  requirementRef: { label: 'Requirement' },
  kind: { label: 'Kind', format: (v) => DECISION_LABEL[v as DecisionKind] ?? capitalize(humanize(String(v))) },
  outcome: { label: 'Outcome', format: (v) => OUTCOME_LABEL[v as Outcome] ?? capitalize(humanize(String(v))) },
  note: { label: 'Note' },
  reason: { label: 'Reason' },
  quote: { label: 'Quote' },
  filename: { label: 'File' },
  pageNumber: { label: 'Page' },
  label: { label: 'Version' },
  name: { label: 'Name' },
  code: { label: 'Course' },
  title: { label: 'Title' },
  courseCode: { label: 'Course' },
  catalogYear: { label: 'Catalog year' },
  required: { label: 'Required', format: yesNo },
  email: { label: 'Email' },
  role: { label: 'Role', format: roleOrValue },
  from: { label: 'From', format: roleOrValue },
  to: { label: 'To', format: roleOrValue },
  method: { label: 'Method', format: (v) => (v === 'oidc' ? 'Single sign-on' : capitalize(String(v))) },
  key: { label: 'Fact', format: (v) => NUMERIC_FIELD_LABEL[v as NumericField] ?? capitalize(humanize(String(v))) },
  value: { label: 'Value' },
  licenseType: { label: 'License', format: (v) => capitalize(humanize(String(v))) },
  effectiveFrom: { label: 'Effective from' },
  jurisdictions: { label: 'Jurisdictions', format: (v) => (Array.isArray(v) ? v.join(', ') : String(v)) },
  requirementCount: { label: 'Requirements' },
  before: { label: 'Before', format: listOf('requirement') },
  after: { label: 'After', format: listOf('requirement') },
  mappingsProduced: { label: 'Proposed findings' },
  pages: { label: 'Pages' },
  pagesInserted: { label: 'Pages stored' },
  ocrPages: { label: 'Pages read by OCR' },
  bytes: { label: 'Size', format: (v) => `${String(v)} bytes` },
  rowCount: { label: 'Rows' },
  complete: { label: 'Complete', format: yesNo },
  reopenedDeterminations: { label: 'Determinations reopened' },
  staleMappings: { label: 'Findings outdated' },
  assessments: { label: 'Assessments' },
  carriedOver: { label: 'Carried over' },
  reevaluated: { label: 'Evaluated again' },
  quotesStillPresent: { label: 'Quotes still present' },
  returnedToReview: { label: 'Back in review' },
  courseVersionCount: { label: 'Course versions' },
  url: { label: 'Address' },
  checkIntervalHours: { label: 'Check interval', format: (v) => `${String(v)} hours` },
  active: { label: 'Active', format: yesNo },
  httpStatus: { label: 'HTTP status' },
  expiresAt: { label: 'Expires' },
  type: { label: 'Job', format: (v) => capitalize(humanize(String(v))) },
  error: { label: 'Error' },
  llmModel: { label: 'Model' },
  model: { label: 'Model' },
  promptVersion: { label: 'Prompt version' },
};

const ORDER = Object.keys(DETAIL_KEYS);

/** Ids and hashes are set in mono: "changeEventId", "sha256", "versionsHash". */
const isMono = (key: string): boolean => /Id$|^sha256$|Hash$/.test(key);

function plain(value: unknown): boolean {
  return ['string', 'number', 'boolean'].includes(typeof value) || (Array.isArray(value) && value.every((v) => ['string', 'number'].includes(typeof v)));
}

/** "changeEventId" is "Change event ID", "sha256" is "SHA-256". */
function keyLabel(key: string): string {
  if (key === 'sha256') { return 'SHA-256'; }
  return capitalize(humanize(key).replace(/ id$/, ' ID'));
}

/** The entry's detail as worded rows: known keys first in reading order, then the rest; empty values and objects left out. */
export function detailRows(detail: Record<string, unknown>, names: (code: string) => string): DetailRow[] {
  const keys = Object.keys(detail).sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
  const rows: DetailRow[] = [];
  for (const key of keys) {
    const value = detail[key];
    const known = DETAIL_KEYS[key];
    if (value === null || value === undefined || (!plain(value) && !(known?.format && Array.isArray(value)))) { continue; }
    const text = known?.format ? known.format(value, names) : Array.isArray(value) ? value.join(', ') : typeof value === 'boolean' ? yesNo(value) : String(value);
    if (text.trim() !== '') { rows.push({ label: known?.label ?? keyLabel(key), value: text, mono: isMono(key) }); }
  }
  return rows;
}

function rank(key: string): number {
  const i = ORDER.indexOf(key);
  return i === -1 ? ORDER.length : i;
}

/**
 * A sign-out recorded before FIX-5 (D-410) carries no user, because it was written after the session had been cleared;
 * its actor is the account it is recorded on, not background work. Sign-outs recorded since carry their actor and take
 * the first branch. Nothing back-fills the old entries (the log is append-only), so this stays as long as they do.
 */
function actorId(e: AuditEntry): string | null {
  if (e.userId !== null) { return e.userId; }
  return e.action === 'LOGOUT' && e.entityType === 'app_user' ? e.entityId : null;
}

/** Entries as the trail shows them: actor resolved to a name, the sentence, the worded details. */
export function auditRows(entries: AuditEntry[], users: UserSummary[], names: (code: string) => string): AuditRow[] {
  const people = new Map(users.map((u) => [u.id, u.displayName]));
  return entries.map((e) => {
    const id = actorId(e);
    const actor = id === null ? 'System' : (people.get(id) ?? 'Former user');
    const sentence = auditSentence(e.action, { actor, detail: e.detail, jurisdictionName: names });
    return {
      id: e.id,
      at: e.createdAt,
      action: e.action,
      actor,
      sentence,
      rest: sentence.startsWith(actor) ? sentence.slice(actor.length) : ` ${sentence}`,
      recordLabel: recordLabel(e.entityType),
      entityId: e.entityId,
      details: detailRows(e.detail, names),
    };
  });
}

export type Period = 'today' | 'week' | 'month';

export const PERIOD_LABEL: Record<Period, string> = { today: 'Today', week: 'Last 7 days', month: 'Last 30 days' };

/** The filters on screen; null means "any". */
export interface AuditQuery { userId: string | null; action: string | null; entityType: string | null; period: Period | null }

export const NO_QUERY: AuditQuery = { userId: null, action: null, entityType: null, period: null };

/** Local midnight at the start of the period: today, the last 7 days or the last 30 days, today included. */
export function periodStart(period: Period, now: Date): Date {
  const back = { today: 0, week: 6, month: 29 }[period];
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() - back);
}

/** Query parameters for GET /audit and /audit.csv: only the filters that are set (D-116). */
export function auditParams(query: AuditQuery, now: Date): Record<string, string> {
  const params: Record<string, string> = {};
  if (query.userId) { params['userId'] = query.userId; }
  if (query.action) { params['action'] = query.action; }
  if (query.entityType) { params['entityType'] = query.entityType; }
  if (query.period) { params['from'] = periodStart(query.period, now).toISOString(); }
  return params;
}

export const AUDIT_CSV_PATH = '/api/v1/audit.csv';

/** The "Download CSV" link for the filters on screen. */
export function auditCsvUrl(query: AuditQuery, now: Date): string {
  const search = new URLSearchParams(auditParams(query, now)).toString();
  return `${AUDIT_CSV_PATH}${search ? `?${search}` : ''}`;
}

export interface Option { value: string; label: string }

const byLabel = (a: Option, b: Option): number => a.label.localeCompare(b.label);

/** Every action the backend audits, by its short name. */
export const ACTION_OPTIONS: Option[] = Object.entries(ACTION_LABEL).map(([value, label]) => ({ value, label })).sort(byLabel);

/** Every record type the backend writes. */
export const RECORD_TYPE_OPTIONS: Option[] = Object.entries(RECORD_TYPE_LABEL).map(([value, label]) => ({ value, label })).sort(byLabel);

export const PERIOD_OPTIONS: Option[] = (Object.keys(PERIOD_LABEL) as Period[]).map((value) => ({ value, label: PERIOD_LABEL[value] }));

/** The tenant's people, by name. */
export function personOptions(users: UserSummary[]): Option[] {
  return users.map((u) => ({ value: u.id, label: u.displayName })).sort(byLabel);
}

/** How much of the head hash the result line prints; the copy button copies all of it. */
export const SHORT_HEAD_LENGTH = 16;

/** The result of "Verify" as the title row shows it: one sentence, whether it holds, and the head hash when one exists. */
export interface VerificationView { sentence: string; intact: boolean; head: { hash: string; short: string } | null }

const count = (n: number): string => n.toLocaleString('en-US');

/**
 * "Intact: 1,204 entries sealed, 3 pending", "Broken at entry 57: (the server's reason)", or, before the first seal,
 * "Nothing sealed yet: 12 pending". The server's reason is shown as it is; the sentence never says more than the walk found.
 */
export function verificationView(v: AuditVerification): VerificationView {
  const head = v.headHash ? { hash: v.headHash, short: v.headHash.slice(0, SHORT_HEAD_LENGTH) } : null;
  if (!v.intact) {
    const where = v.firstBrokenSeq === null ? 'Broken' : `Broken at entry ${count(v.firstBrokenSeq)}`;
    return { sentence: `${where}: ${v.firstBrokenReason ?? 'a seal does not hold'}`, intact: false, head };
  }
  if (v.sealed === 0) { return { sentence: `Nothing sealed yet: ${count(v.pending)} pending`, intact: true, head }; }
  return { sentence: `Intact: ${count(v.sealed)} ${v.sealed === 1 ? 'entry' : 'entries'} sealed, ${count(v.pending)} pending`, intact: true, head };
}
