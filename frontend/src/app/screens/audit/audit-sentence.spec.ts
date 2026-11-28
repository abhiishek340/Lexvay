import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { ACTION_LABEL, AUDIT_SENTENCES, actionLabel, auditSentence } from './audit-sentence';

const NAMES: Record<string, string> = { CA: 'California', CO: 'Colorado' };
const names = (code: string): string => NAMES[code] ?? code;
const say = (action: string, detail: Record<string, unknown> = {}, actor = 'Dana Whitfield'): string =>
  auditSentence(action, { actor, detail, jurisdictionName: names });

/**
 * Every action name the backend audits, read from its sources: the Java calls `audit.record(...)` and `recordAs`, and
 * the migrations whose triggers insert into audit_log themselves.
 */
function backendActions(): string[] {
  const main = resolve(process.cwd(), '../backend/src/main');
  const files = (dir: string, extension: string): string[] => readdirSync(dir, { withFileTypes: true })
    .flatMap((e) => (e.isDirectory() ? files(join(dir, e.name), extension) : e.name.endsWith(extension) ? [join(dir, e.name)] : []));
  const actions = new Set<string>();
  for (const file of files(join(main, 'java'), '.java')) {
    // The action is the upper-case string before the record type (the first lower-case string) of the call.
    for (const [, head] of readFileSync(file, 'utf8').matchAll(/audit\.record(?:As)?\(([^;]*?)"[a-z_]+"/gs)) {
      for (const [, action] of head.matchAll(/"([A-Z][A-Z_]+)"/g)) { actions.add(action); }
    }
  }
  for (const file of files(join(main, 'resources/db/migration'), '.sql')) {
    // The action is the first quoted upper-case word of the VALUES of an INSERT INTO audit_log.
    for (const [, action] of readFileSync(file, 'utf8').matchAll(/INSERT INTO audit_log[^;]*?VALUES\s*\([^']*?'([A-Z][A-Z_]+)'/gs)) { actions.add(action); }
  }
  return [...actions].sort();
}

describe('audit sentences', () => {
  it('cover every action the backend audits, each with a short name', () => {
    const audited = backendActions();
    expect(audited.length).toBeGreaterThan(30);
    expect(Object.keys(AUDIT_SENTENCES).sort()).toEqual(audited);
    expect(Object.keys(ACTION_LABEL).sort()).toEqual(audited);
  });

  it('read as one sentence that starts with who acted', () => {
    const cases: [string, Record<string, unknown>, string][] = [
      ['LOGIN_SUCCEEDED', { email: 'dana@x', method: 'password' }, 'Dana Whitfield signed in'],
      ['LOGIN_SUCCEEDED', { email: 'dana@x', method: 'oidc' }, 'Dana Whitfield signed in with single sign-on'],
      ['LOGOUT', {}, 'Dana Whitfield signed out'],
      ['PASSWORD_SET', {}, 'Dana Whitfield set a password'],
      ['PASSWORD_REHASHED', { from: 'bcrypt', to: 'argon2' }, "Dana Whitfield's password hash was upgraded at sign-in"],
      ['TENANT_CREATED', { name: 'Northfield University' }, 'Dana Whitfield created the organization Northfield University'],
      ['USER_CREATED', { email: 'jordan@x', role: 'CONTRIBUTOR' }, 'Dana Whitfield added jordan@x as a contributor'],
      ['USER_CREATED', { email: 'admin@x', role: 'ADMIN' }, 'Dana Whitfield added admin@x as an administrator'],
      ['USER_SETUP_CODE_ISSUED', { email: 'jordan@x', expiresAt: '2026-11-30T10:00:00Z' }, 'Dana Whitfield issued a setup code for jordan@x'],
      ['USER_ROLE_CHANGED', { email: 'jordan@x', from: 'REVIEWER', to: 'SIGNER' }, 'Dana Whitfield changed jordan@x from reviewer to signer'],
      ['USER_DEACTIVATED', { email: 'jordan@x' }, 'Dana Whitfield withdrew access for jordan@x'],
      ['USER_REACTIVATED', { email: 'jordan@x' }, 'Dana Whitfield restored access for jordan@x'],
      ['PROGRAM_CREATED', { name: 'M.S. Counseling', jurisdictions: ['CA'] }, 'Dana Whitfield created the program M.S. Counseling'],
      ['COURSE_CREATED', { code: 'COUN 625', title: 'Telehealth' }, 'Dana Whitfield added the course COUN 625'],
      ['COURSE_VERSION_ADDED', { catalogYear: '2027-28' }, 'Dana Whitfield added catalog year 2027-28 of a course'],
      ['DOCUMENT_UPLOADED', { filename: 'coun625.pdf', kind: 'SYLLABUS' }, 'Dana Whitfield uploaded coun625.pdf'],
      ['DOCUMENT_INGESTED', { pages: 2, ocrPages: 0 }, 'Dana Whitfield extracted the text of 2 pages'],
      ['DOCUMENT_INGESTED', { pages: 3, ocrPages: 1 }, 'Dana Whitfield extracted the text of 3 pages, 1 by OCR'],
      ['PROGRAM_FACT_RECORDED', { key: 'TOTAL_CREDITS', value: '60' }, 'Dana Whitfield recorded total credits as 60'],
      ['RULE_VERSION_CREATED', { jurisdiction: 'CO', label: 'Colorado 2026 revision 2' }, 'Dana Whitfield entered the rule version Colorado 2026 revision 2'],
      ['REQUIREMENTS_EXTRACTED', { requirementCount: 9 }, 'Dana Whitfield extracted 9 requirements'],
      ['REQUIREMENTS_REPLACED', { before: [{}, {}], after: [{}, {}, {}] }, 'Dana Whitfield edited a requirement list (3 requirements)'],
      ['REQUIREMENTS_APPROVED', { requirementCount: 1 }, 'Dana Whitfield approved 1 requirement'],
      ['RULE_STATUS_RECORDED', { legalStatus: 'REPEALED', previousLegalStatus: 'IN_FORCE' }, 'Dana Whitfield recorded a rule version as repealed'],
      ['RULE_STATUS_RECORDED', { legalStatus: 'FINAL_NOT_EFFECTIVE' }, 'Dana Whitfield recorded a rule version as final, not yet in effect'],
      ['RULE_SOURCE_CHECKED', { outcome: 'CHANGED_QUEUED' }, 'Dana Whitfield checked a rule source: new text, kept behind the draft that waits for review'],
      ['RULE_SOURCE_CREATED', { jurisdiction: 'CO', url: 'https://example.invalid' }, 'Dana Whitfield added a rule source for Colorado'],
      ['RULE_SOURCE_CHECK_REQUESTED', { jobId: 'j' }, 'Dana Whitfield asked for a rule source check'],
      ['RULE_SOURCE_ACTIVE_CHANGED', { active: false }, 'Dana Whitfield paused a rule source'],
      ['RULE_SOURCE_ACTIVE_CHANGED', { active: true }, 'Dana Whitfield resumed a rule source'],
      ['RULE_SOURCE_CHECKED', { outcome: 'UNCHANGED' }, 'Dana Whitfield checked a rule source: no change'],
      ['RULE_SOURCE_CHECKED', { outcome: 'FETCH_FAILED', httpStatus: 503 }, 'Dana Whitfield checked a rule source: it could not be fetched'],
      ['ASSESSMENT_STARTED', { jurisdictions: ['CA', 'CO'] }, 'Dana Whitfield started an assessment of 2 jurisdictions'],
      ['ASSESSMENT_FINISHED', { mappingsProduced: 40 }, 'Dana Whitfield finished an assessment with 40 proposed findings'],
      ['REVIEW_RECORDED', { kind: 'ACCEPTED', requirementRef: 'CA-3' }, 'Dana Whitfield accepted CA-3'],
      ['REVIEW_RECORDED', { kind: 'EVIDENCE_REQUESTED', requirementRef: 'CA-4' }, 'Dana Whitfield requested evidence for CA-4'],
      ['REVIEW_RECORDED', { kind: 'GAP_CONFIRMED', requirementRef: 'CA-5', note: 'None' }, 'Dana Whitfield confirmed a gap on CA-5'],
      ['EVIDENCE_PROVIDED', { requirementRef: 'UT-2', quote: 'Students complete' }, 'Dana Whitfield provided evidence for UT-2'],
      ['DETERMINATION_SIGNED', { jurisdiction: 'CA', outcome: 'DOES_NOT_MEET' }, 'Dana Whitfield signed California: determined not to meet'],
      ['DETERMINATION_REOPENED', { reason: 'New rule version: r2' }, 'Dana Whitfield reopened a signed determination with a change'],
      ['REGISTER_EXPORTED', { rowCount: 1 }, 'Dana Whitfield downloaded the determination register (1 row)'],
      ['AUDIT_EXPORTED', { rowCount: 250, complete: true }, 'Dana Whitfield downloaded the audit trail (250 entries)'],
      ['AUDIT_EXPORTED', { rowCount: 1, complete: false }, 'Dana Whitfield started an audit trail download that stopped after 1 entry'],
      ['AUDIT_VERIFIED', { intact: true, sealed: 250, headHash: 'a'.repeat(64) }, 'Dana Whitfield verified the audit trail: intact'],
      ['AUDIT_VERIFIED', { intact: false, sealed: 250, firstBrokenSeq: 17 }, 'Dana Whitfield verified the audit trail: broken at entry 17'],
      ['AUDIT_VERIFIED', { intact: false }, 'Dana Whitfield verified the audit trail: broken'],
      ['AUDIT_VERIFIED', {}, 'Dana Whitfield verified the audit trail'],
      ['CHANGE_EVENT_CREATED', { kind: 'RULE_VERSION', reopenedDeterminations: 1 }, 'Dana Whitfield recorded a new rule version, reopening 1 signed determination'],
      ['CHANGE_EVENT_CREATED', { kind: 'COURSE_VERSION', reopenedDeterminations: 0 }, 'Dana Whitfield recorded a new course version'],
      ['CHANGE_RERUN_REQUESTED', { kind: 'RULE_VERSION' }, 'Dana Whitfield re-ran the assessment for a new rule version'],
      ['CHANGE_RERUN_REQUESTED', { kind: 'COURSE_VERSION' }, 'Dana Whitfield re-ran matching for a new course version'],
      ['REPORT_REQUESTED', { determinationId: 'd' }, 'Dana Whitfield requested an evidence report PDF'],
      ['PACKAGE_REQUESTED', { determinationId: 'd', jobId: 'j' }, 'Dana Whitfield requested an evidence package'],
      ['PACKAGE_DOWNLOADED', { determinationId: 'd', bytes: 2048 }, 'Dana Whitfield downloaded an evidence package'],
      ['JOB_FAILED', { type: 'BUILD_PACKAGE', error: 'too large' }, 'Dana Whitfield could not finish an evidence package'],
      ['REPORT_CHECKED', { match: true, sha256: 'a'.repeat(64) }, 'Dana Whitfield checked a report file: it matches'],
      ['REPORT_CHECKED', { match: false, sha256: 'b'.repeat(64) }, 'Dana Whitfield checked a report file: no match'],
      ['JOB_FAILED', { type: 'RUN_ASSESSMENT', error: 'boom' }, 'Dana Whitfield could not finish an assessment'],
      ['LLM_CALL_REFUSED', { reason: 'NO_MODEL_FOR_REAL_DATA' }, 'Dana Whitfield did not make a model call: no model is configured for real data'],
      ['LLM_CALL_REFUSED', { reason: 'OVER_BUDGET' }, 'Dana Whitfield did not make a model call: it would pass the monthly budget'],
      ['LLM_CALL_REFUSED', { reason: 'PAID_CALLS_NOT_APPROVED' }, 'Dana Whitfield did not make a model call: paid model calls are not approved'],
      ['LLM_CALL_REFUSED', { reason: 'COST_UNKNOWN' }, 'Dana Whitfield did not make a model call: its cost is unknown'],
      ['TENANT_DATA_MODE_CHANGED', { from: 'DEMO', to: 'LIVE' }, "Dana Whitfield changed the organization's data from demo to real"],
    ];
    for (const [action, detail, sentence] of cases) {
      expect(say(action, detail), action).toBe(sentence);
    }
  });

  it('words the work of background jobs with System as the actor', () => {
    expect(say('CHANGE_RERUN', { kind: 'RULE_VERSION', carriedOver: 7, reevaluated: 2, assessments: 1 }, 'System'))
      .toBe('System finished a re-run: 7 carried over, 2 evaluated again');
    expect(say('CHANGE_RERUN', { kind: 'COURSE_VERSION', courseCode: 'COUN 625', quotesStillPresent: 1, returnedToReview: 2 }, 'System'))
      .toBe('System finished re-running COUN 625: 1 quote still present, 2 back in review');
    expect(say('REPORT_GENERATED', { bytes: 2048 }, 'System')).toBe('System generated an evidence report PDF');
    expect(say('PACKAGE_READY', { bytes: 4096, sha256: 'a' }, 'System')).toBe('System built an evidence package');
  });

  it('falls back to a general noun when a detail is missing, never to "undefined" or raw values', () => {
    for (const action of Object.keys(AUDIT_SENTENCES)) {
      for (const detail of [{}, { kind: { nested: true }, jurisdiction: 42, requirementRef: '' }]) {
        const sentence = say(action, detail);
        expect(sentence, action).toMatch(/^Dana Whitfield\b/);
        expect(sentence, action).not.toMatch(/undefined|null|NaN|\[object|[{}[\]]/);
      }
    }
    expect(say('REVIEW_RECORDED', {})).toBe('Dana Whitfield recorded a decision on a requirement');
    expect(say('USER_ROLE_CHANGED', { email: 'jordan@x' })).toBe('Dana Whitfield changed the role of jordan@x');
  });

  it('reads an unknown action by its words and never prints its detail', () => {
    const sentence = say('SOMETHING_NEW_HAPPENED', { algorithm: 'argon2id', secret: { a: 1 } });
    expect(sentence).toBe('Dana Whitfield: Something new happened');
    expect(actionLabel('SOMETHING_NEW')).toBe('Something new');
  });
});
