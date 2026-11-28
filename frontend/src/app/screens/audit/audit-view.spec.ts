import { describe, expect, it } from 'vitest';
import { AuditEntry, UserSummary } from '../../core/models';
import {
  ACTION_OPTIONS, NO_QUERY, RECORD_TYPE_OPTIONS, auditCsvUrl, auditParams, auditRows, detailRows, periodStart, personOptions, verificationView,
} from './audit-view';

const users: UserSummary[] = [
  { id: 'u1', displayName: 'Dana Whitfield', role: 'SIGNER', email: 'dana@northfield.example' },
  { id: 'u2', displayName: 'Dr. Priya Raman', role: 'REVIEWER', email: 'priya@northfield.example' },
];
const names = (code: string): string => ({ CA: 'California' }[code] ?? code);

/** A local time on a day of November 2026, as the ISO instant the server sends. */
const at = (day: number, hour: number): string => new Date(2026, 10, day, hour, 0).toISOString();

function entry(id: string, action: string, entityType: string, createdAt: string, userId: string | null, detail: Record<string, unknown> = {}): AuditEntry {
  return { id, userId, action, entityType, entityId: 'e-' + id, detail, createdAt };
}

const entries = [
  entry('1', 'DETERMINATION_SIGNED', 'determination', at(27, 11), 'u1', { jurisdiction: 'CA', outcome: 'DOES_NOT_MEET', versionsHash: 'abc' }),
  entry('2', 'REVIEW_RECORDED', 'mapping', at(27, 10), 'u2', { requirementRef: 'CA-3', kind: 'GAP_CONFIRMED', note: 'No telehealth content.', reviewEventId: 'r1' }),
  entry('3', 'ASSESSMENT_FINISHED', 'assessment', at(26, 9), null, { mappingsProduced: 40, jobId: 'j1' }),
  entry('4', 'LOGIN_SUCCEEDED', 'app_user', at(20, 8), 'gone', { email: 'old@northfield.example', method: 'password' }),
];

describe('audit rows', () => {
  const rows = auditRows(entries, users, names);

  it('resolves actors to names; background work is System, unknown ids a former user', () => {
    expect(rows.map((r) => r.actor)).toEqual(['Dana Whitfield', 'Dr. Priya Raman', 'System', 'Former user']);
  });

  it('names the person on a sign-out, recorded with its actor since FIX-5 or without one before, never "System"', () => {
    const [recorded, legacy, job] = auditRows([
      { ...entry('5', 'LOGOUT', 'app_user', at(27, 12), 'u1'), entityId: 'u1' },
      { ...entry('6', 'LOGOUT', 'app_user', at(26, 12), null), entityId: 'u2' },
      entry('7', 'REPORT_GENERATED', 'report', at(26, 12), null),
    ], users, names);
    expect([recorded.actor, recorded.sentence]).toEqual(['Dana Whitfield', 'Dana Whitfield signed out']);
    expect([legacy.actor, legacy.sentence]).toEqual(['Dr. Priya Raman', 'Dr. Priya Raman signed out']);
    expect(job.actor).toBe('System');
  });

  it('does not borrow an account for other actions recorded without a user', () => {
    const [signIn] = auditRows([{ ...entry('8', 'LOGIN_SUCCEEDED', 'app_user', at(27, 12), null), entityId: 'u1' }], users, names);
    expect(signIn.actor).toBe('System');
  });

  it('carries one sentence per entry and the record type in words', () => {
    expect(rows.map((r) => r.sentence)).toEqual([
      'Dana Whitfield signed California: determined not to meet',
      'Dr. Priya Raman confirmed a gap on CA-3',
      'System finished an assessment with 40 proposed findings',
      'Former user signed in',
    ]);
    expect(rows[1].rest).toBe(' confirmed a gap on CA-3');
    expect(rows.map((r) => r.recordLabel)).toEqual(['Determination', 'Requirement finding', 'Assessment', 'User']);
  });
});

describe('detail rows', () => {
  it('word the known keys in reading order, ids and hashes in mono', () => {
    expect(detailRows(entries[1].detail, names)).toEqual([
      { label: 'Requirement', value: 'CA-3', mono: false },
      { label: 'Kind', value: 'Gap confirmed', mono: false },
      { label: 'Note', value: 'No telehealth content.', mono: false },
      { label: 'Review event ID', value: 'r1', mono: true },
    ]);
    expect(detailRows(entries[0].detail, names)).toEqual([
      { label: 'Jurisdiction', value: 'California (CA)', mono: false },
      { label: 'Outcome', value: 'Determined not to meet', mono: false },
      { label: 'Versions hash', value: 'abc', mono: true },
    ]);
  });

  it('summarise lists, word yes and no, and leave out empty values and nested objects', () => {
    expect(detailRows({ before: [{ ref: 'A' }], after: [{ ref: 'A' }, { ref: 'B' }], note: '', extra: { a: 1 }, complete: false, required: true }, names)).toEqual([
      { label: 'Required', value: 'Yes', mono: false },
      { label: 'Before', value: '1 requirement', mono: false },
      { label: 'After', value: '2 requirements', mono: false },
      { label: 'Complete', value: 'No', mono: false },
    ]);
    expect(detailRows({ sha256: 'f00', somethingNew: 'x', tags: ['a', 'b'] }, names)).toEqual([
      { label: 'SHA-256', value: 'f00', mono: true },
      { label: 'Something new', value: 'x', mono: false },
      { label: 'Tags', value: 'a, b', mono: false },
    ]);
  });

  it('read roles where a role is stored and dates where a filter is', () => {
    expect(detailRows({ email: 'j@x', from: 'REVIEWER', to: 'SIGNER' }, names).map((r) => r.value)).toEqual(['j@x', 'Reviewer', 'Signer']);
    expect(detailRows({ from: '2026-11-01T00:00:00Z' }, names).map((r) => r.value)).toEqual(['2026-11-01T00:00:00Z']);
  });
});

describe('server-side filters', () => {
  const now = new Date(2026, 10, 27, 15, 30);

  it('send only the filters that are set, the period as its local start', () => {
    expect(auditParams(NO_QUERY, now)).toEqual({});
    expect(periodStart('today', now)).toEqual(new Date(2026, 10, 27));
    expect(periodStart('week', now)).toEqual(new Date(2026, 10, 21));
    expect(periodStart('month', now)).toEqual(new Date(2026, 9, 29));
    expect(auditParams({ userId: 'u1', action: 'REVIEW_RECORDED', entityType: 'mapping', period: 'week' }, now)).toEqual({
      userId: 'u1', action: 'REVIEW_RECORDED', entityType: 'mapping', from: new Date(2026, 10, 21).toISOString(),
    });
  });

  it('build the CSV link with the same filters', () => {
    expect(auditCsvUrl(NO_QUERY, now)).toBe('/api/v1/audit.csv');
    expect(auditCsvUrl({ ...NO_QUERY, action: 'DETERMINATION_SIGNED', userId: 'u1' }, now)).toBe('/api/v1/audit.csv?userId=u1&action=DETERMINATION_SIGNED');
  });

  it('offer every audited action and record type once, and people by name', () => {
    expect(new Set(ACTION_OPTIONS.map((o) => o.value)).size).toBe(ACTION_OPTIONS.length);
    expect(ACTION_OPTIONS.find((o) => o.value === 'REVIEW_RECORDED')?.label).toBe('Review decision recorded');
    expect(RECORD_TYPE_OPTIONS.map((o) => o.label)).toEqual([...RECORD_TYPE_OPTIONS.map((o) => o.label)].sort((a, b) => a.localeCompare(b)));
    expect(personOptions(users).map((o) => o.label)).toEqual(['Dana Whitfield', 'Dr. Priya Raman']);
  });
});

describe('verification', () => {
  const sealed = {
    sealed: 1204, pending: 3, intact: true, firstBrokenSeq: null, firstBrokenReason: null, lastSealedAt: '2026-11-28T09:00:00Z', headHash: 'ab'.repeat(32),
  };

  it('says an intact chain in one sentence, with the head hash shortened', () => {
    expect(verificationView(sealed)).toEqual({ sentence: 'Intact: 1,204 entries sealed, 3 pending', intact: true, head: { hash: 'ab'.repeat(32), short: 'ab'.repeat(8) } });
    expect(verificationView({ ...sealed, sealed: 1, pending: 0 }).sentence).toBe('Intact: 1 entry sealed, 0 pending');
  });

  it('names the first broken seal and its reason', () => {
    expect(verificationView({ ...sealed, intact: false, firstBrokenSeq: 57, firstBrokenReason: 'the row hash does not match' }))
      .toMatchObject({ sentence: 'Broken at entry 57: the row hash does not match', intact: false });
  });

  it('says nothing is sealed yet, without a head hash', () => {
    expect(verificationView({ ...sealed, sealed: 0, pending: 12, lastSealedAt: null, headHash: null }))
      .toEqual({ sentence: 'Nothing sealed yet: 12 pending', intact: true, head: null });
  });
});
