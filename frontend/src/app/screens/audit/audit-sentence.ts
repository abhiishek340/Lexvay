import { DECISION_LABEL, NUMERIC_FIELD_LABEL, OUTCOME_LABEL, ROLE_LABEL, humanize } from '../../core/labels';
import { DecisionKind, NumericField, Outcome, Role } from '../../core/models';

/** What a sentence is built from: who acted (a name, "System" or "Former user") and the entry's stored detail. */
export interface SentenceInput {
  actor: string;
  detail: Record<string, unknown>;
  /** "CA" becomes "California"; an unknown code stays as it is. */
  jurisdictionName: (code: string) => string;
}

type Sentence = (input: SentenceInput) => string;

/** A detail value as text when it is a non-empty string or a number; anything else (missing, an object) is null. */
function text(detail: Record<string, unknown>, key: string): string | null {
  const value = detail[key];
  if (typeof value === 'number' && Number.isFinite(value)) { return String(value); }
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null;
}

function number(detail: Record<string, unknown>, key: string): number | null {
  const value = detail[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/** "1 requirement", "9 requirements"; null when the count is missing. */
function count(n: number | null, singular: string, plural = `${singular}s`): string | null {
  return n === null ? null : `${n} ${n === 1 ? singular : plural}`;
}

/** "a signer", "an administrator". */
function roleWithArticle(role: unknown): string {
  const label = (ROLE_LABEL[role as Role] ?? 'member').toLowerCase();
  return `${/^[aeiou]/.test(label) ? 'an' : 'a'} ${label}`;
}

const ref = (d: Record<string, unknown>): string => text(d, 'requirementRef') ?? 'a requirement';
const email = (d: Record<string, unknown>): string => text(d, 'email') ?? 'a user';
const withCount = (base: string, n: string | null): string => (n ? `${base} (${n})` : base);
const isRule = (d: Record<string, unknown>): boolean => d['kind'] !== 'COURSE_VERSION';

const JOB_NOUN: Record<string, string> = {
  INGEST_DOCUMENT: 'reading a document',
  EXTRACT_REQUIREMENTS: 'extracting requirements',
  RUN_ASSESSMENT: 'an assessment',
  RERUN_CHANGE: 'a re-run',
  GENERATE_REPORT: 'an evidence report PDF',
  BUILD_PACKAGE: 'an evidence package',
  CHECK_RULE_SOURCE: 'a rule source check',
};

const CHECK_RESULT: Record<string, string> = {
  UNCHANGED: 'no change',
  CHANGED: 'new text, a draft rule version was created',
  DRAFT_EXISTS: 'new text, a draft already waits for review',
  FETCH_FAILED: 'it could not be fetched',
};

/**
 * One sentence per audited action, starting with who acted: "Dana Whitfield accepted CA-3". Every action the backend
 * records has an entry (audit-sentence.spec.ts reads the backend's sources to prove it). A sentence never prints a raw
 * value it does not understand; a missing detail falls back to a general noun ("a requirement").
 */
export const AUDIT_SENTENCES: Record<string, Sentence> = {
  LOGIN_SUCCEEDED: ({ actor, detail }) => (detail['method'] === 'oidc' ? `${actor} signed in with single sign-on` : `${actor} signed in`),
  LOGOUT: ({ actor }) => `${actor} signed out`,
  PASSWORD_SET: ({ actor }) => `${actor} set a password`,
  PASSWORD_REHASHED: ({ actor }) => `${actor}'s password hash was upgraded at sign-in`,
  TENANT_CREATED: ({ actor, detail }) => `${actor} created the organization${text(detail, 'name') ? ` ${text(detail, 'name')}` : ''}`,
  USER_CREATED: ({ actor, detail }) => `${actor} added ${email(detail)} as ${roleWithArticle(detail['role'])}`,
  USER_SETUP_CODE_ISSUED: ({ actor, detail }) => `${actor} issued a setup code for ${email(detail)}`,
  USER_ROLE_CHANGED: ({ actor, detail }) => {
    const from = ROLE_LABEL[detail['from'] as Role];
    const to = ROLE_LABEL[detail['to'] as Role];
    return from && to ? `${actor} changed ${email(detail)} from ${from.toLowerCase()} to ${to.toLowerCase()}` : `${actor} changed the role of ${email(detail)}`;
  },
  USER_DEACTIVATED: ({ actor, detail }) => `${actor} withdrew access for ${email(detail)}`,
  USER_REACTIVATED: ({ actor, detail }) => `${actor} restored access for ${email(detail)}`,
  PROGRAM_CREATED: ({ actor, detail }) => `${actor} created the program${text(detail, 'name') ? ` ${text(detail, 'name')}` : ''}`,
  COURSE_CREATED: ({ actor, detail }) => `${actor} added the course${text(detail, 'code') ? ` ${text(detail, 'code')}` : ''}`,
  COURSE_VERSION_ADDED: ({ actor, detail }) =>
    text(detail, 'catalogYear') ? `${actor} added catalog year ${text(detail, 'catalogYear')} of a course` : `${actor} added a course version`,
  DOCUMENT_UPLOADED: ({ actor, detail }) => `${actor} uploaded ${text(detail, 'filename') ?? 'a document'}`,
  DOCUMENT_INGESTED: ({ actor, detail }) => {
    const pages = count(number(detail, 'pages'), 'page');
    const ocr = number(detail, 'ocrPages');
    const base = pages ? `${actor} extracted the text of ${pages}` : `${actor} extracted the text of a document`;
    return ocr ? `${base}, ${ocr} by OCR` : base;
  },
  PROGRAM_FACT_RECORDED: ({ actor, detail }) => {
    const field = NUMERIC_FIELD_LABEL[detail['key'] as NumericField];
    const value = text(detail, 'value');
    return field && value ? `${actor} recorded ${field.toLowerCase()} as ${value}` : `${actor} recorded a program fact`;
  },
  RULE_VERSION_CREATED: ({ actor, detail }) => `${actor} entered the rule version${text(detail, 'label') ? ` ${text(detail, 'label')}` : ''}`,
  REQUIREMENTS_EXTRACTED: ({ actor, detail }) => `${actor} extracted ${count(number(detail, 'requirementCount'), 'requirement') ?? 'requirements'}`,
  REQUIREMENTS_REPLACED: ({ actor, detail }) => {
    const after = Array.isArray(detail['after']) ? detail['after'].length : null;
    return withCount(`${actor} edited a requirement list`, count(after, 'requirement'));
  },
  REQUIREMENTS_APPROVED: ({ actor, detail }) => `${actor} approved ${count(number(detail, 'requirementCount'), 'requirement') ?? 'a requirement list'}`,
  RULE_SOURCE_CREATED: ({ actor, detail, jurisdictionName }) =>
    text(detail, 'jurisdiction') ? `${actor} added a rule source for ${jurisdictionName(text(detail, 'jurisdiction')!)}` : `${actor} added a rule source`,
  RULE_SOURCE_CHECK_REQUESTED: ({ actor }) => `${actor} asked for a rule source check`,
  RULE_SOURCE_ACTIVE_CHANGED: ({ actor, detail }) => (detail['active'] === false ? `${actor} paused a rule source` : `${actor} resumed a rule source`),
  RULE_SOURCE_CHECKED: ({ actor, detail }) => {
    const result = CHECK_RESULT[String(detail['outcome'])];
    return result ? `${actor} checked a rule source: ${result}` : `${actor} checked a rule source`;
  },
  ASSESSMENT_STARTED: ({ actor, detail }) => {
    const jurisdictions = Array.isArray(detail['jurisdictions']) ? detail['jurisdictions'].length : null;
    return `${actor} started an assessment${jurisdictions ? ` of ${count(jurisdictions, 'jurisdiction')}` : ''}`;
  },
  ASSESSMENT_FINISHED: ({ actor, detail }) => {
    const findings = count(number(detail, 'mappingsProduced'), 'proposed finding');
    return findings ? `${actor} finished an assessment with ${findings}` : `${actor} finished an assessment`;
  },
  REVIEW_RECORDED: ({ actor, detail }) => {
    const decided: Record<DecisionKind, string> = {
      ACCEPTED: `${actor} accepted ${ref(detail)}`,
      EVIDENCE_REQUESTED: `${actor} requested evidence for ${ref(detail)}`,
      GAP_CONFIRMED: `${actor} confirmed a gap on ${ref(detail)}`,
    };
    return decided[detail['kind'] as DecisionKind] ?? `${actor} recorded a decision on ${ref(detail)}`;
  },
  EVIDENCE_PROVIDED: ({ actor, detail }) => `${actor} provided evidence for ${ref(detail)}`,
  DETERMINATION_SIGNED: ({ actor, detail, jurisdictionName }) => {
    const where = text(detail, 'jurisdiction') ? jurisdictionName(text(detail, 'jurisdiction')!) : 'a jurisdiction';
    const outcome = OUTCOME_LABEL[detail['outcome'] as Outcome];
    return outcome ? `${actor} signed ${where}: ${outcome.toLowerCase()}` : `${actor} signed ${where}`;
  },
  DETERMINATION_REOPENED: ({ actor }) => `${actor} reopened a signed determination with a change`,
  REGISTER_EXPORTED: ({ actor, detail }) => withCount(`${actor} downloaded the determination register`, count(number(detail, 'rowCount'), 'row')),
  AUDIT_EXPORTED: ({ actor, detail }) => {
    const rows = count(number(detail, 'rowCount'), 'entry', 'entries');
    return detail['complete'] === false
      ? `${actor} started an audit trail download that stopped${rows ? ` after ${rows}` : ''}`
      : withCount(`${actor} downloaded the audit trail`, rows);
  },
  CHANGE_EVENT_CREATED: ({ actor, detail }) => {
    const base = `${actor} recorded a new ${isRule(detail) ? 'rule' : 'course'} version`;
    const reopened = number(detail, 'reopenedDeterminations');
    return reopened ? `${base}, reopening ${count(reopened, 'signed determination')}` : base;
  },
  CHANGE_RERUN_REQUESTED: ({ actor, detail }) =>
    isRule(detail) ? `${actor} re-ran the assessment for a new rule version` : `${actor} re-ran matching for a new course version`,
  CHANGE_RERUN: ({ actor, detail }) => {
    if (isRule(detail)) {
      const carried = number(detail, 'carriedOver');
      const fresh = number(detail, 'reevaluated');
      return carried !== null && fresh !== null
        ? `${actor} finished a re-run: ${carried} carried over, ${fresh} evaluated again`
        : `${actor} finished a re-run for a new rule version`;
    }
    const course = text(detail, 'courseCode') ?? 'a course';
    const kept = number(detail, 'quotesStillPresent');
    const back = number(detail, 'returnedToReview');
    return kept !== null && back !== null
      ? `${actor} finished re-running ${course}: ${count(kept, 'quote')} still present, ${back} back in review`
      : `${actor} finished re-running ${course}`;
  },
  REPORT_REQUESTED: ({ actor }) => `${actor} requested an evidence report PDF`,
  REPORT_GENERATED: ({ actor }) => `${actor} generated an evidence report PDF`,
  PACKAGE_REQUESTED: ({ actor }) => `${actor} requested an evidence package`,
  PACKAGE_READY: ({ actor }) => `${actor} built an evidence package`,
  PACKAGE_DOWNLOADED: ({ actor }) => `${actor} downloaded an evidence package`,
  REPORT_CHECKED: ({ actor, detail }) => `${actor} checked a report file: ${detail['match'] === true ? 'it matches' : 'no match'}`,
  JOB_FAILED: ({ actor, detail }) => `${actor} could not finish ${JOB_NOUN[String(detail['type'])] ?? 'a background job'}`,
};

/** The short name of each action, for the filter: sentence case, at most four words. */
export const ACTION_LABEL: Record<string, string> = {
  LOGIN_SUCCEEDED: 'Signed in',
  LOGOUT: 'Signed out',
  PASSWORD_SET: 'Password set',
  PASSWORD_REHASHED: 'Password hash upgraded',
  TENANT_CREATED: 'Organization created',
  USER_CREATED: 'User added',
  USER_SETUP_CODE_ISSUED: 'Setup code issued',
  USER_ROLE_CHANGED: 'Role changed',
  USER_DEACTIVATED: 'Access withdrawn',
  USER_REACTIVATED: 'Access restored',
  PROGRAM_CREATED: 'Program created',
  COURSE_CREATED: 'Course created',
  COURSE_VERSION_ADDED: 'Course version added',
  DOCUMENT_UPLOADED: 'Document uploaded',
  DOCUMENT_INGESTED: 'Document text extracted',
  PROGRAM_FACT_RECORDED: 'Program fact recorded',
  RULE_VERSION_CREATED: 'Rule version entered',
  REQUIREMENTS_EXTRACTED: 'Requirements extracted',
  REQUIREMENTS_REPLACED: 'Requirement list replaced',
  REQUIREMENTS_APPROVED: 'Requirement list approved',
  RULE_SOURCE_CREATED: 'Rule source added',
  RULE_SOURCE_CHECK_REQUESTED: 'Rule source check requested',
  RULE_SOURCE_ACTIVE_CHANGED: 'Rule source paused or resumed',
  RULE_SOURCE_CHECKED: 'Rule source checked',
  ASSESSMENT_STARTED: 'Assessment started',
  ASSESSMENT_FINISHED: 'Assessment finished',
  REVIEW_RECORDED: 'Review decision recorded',
  EVIDENCE_PROVIDED: 'Evidence provided',
  DETERMINATION_SIGNED: 'Determination signed',
  DETERMINATION_REOPENED: 'Determination reopened',
  REGISTER_EXPORTED: 'Register downloaded',
  AUDIT_EXPORTED: 'Audit trail downloaded',
  CHANGE_EVENT_CREATED: 'Change recorded',
  CHANGE_RERUN_REQUESTED: 'Re-run requested',
  CHANGE_RERUN: 'Re-run finished',
  REPORT_REQUESTED: 'Evidence report requested',
  REPORT_GENERATED: 'Evidence report generated',
  PACKAGE_REQUESTED: 'Evidence package requested',
  PACKAGE_READY: 'Evidence package built',
  PACKAGE_DOWNLOADED: 'Evidence package downloaded',
  REPORT_CHECKED: 'Report file checked',
  JOB_FAILED: 'Background job failed',
};

/** "Some new action" for an action this build does not know yet. */
export function actionLabel(action: string): string {
  return ACTION_LABEL[action] ?? capitalize(humanize(action));
}

/**
 * The entry as one sentence. An action this build does not know yet reads "Dana Whitfield: Something new": the
 * action's words, never its detail, so no raw JSON reaches the screen.
 */
export function auditSentence(action: string, input: SentenceInput): string {
  const sentence = AUDIT_SENTENCES[action];
  return sentence ? sentence(input) : `${input.actor}: ${actionLabel(action)}`;
}

export function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}
