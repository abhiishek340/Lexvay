import { describe, expect, it } from 'vitest';
import { AuditEntry, UserSummary } from '../../core/models';
import { auditRows, detailSummary, filterOptions, filterRows } from './audit-view';

const users: UserSummary[] = [{ id: 'u1', displayName: 'Dana Whitfield', role: 'SIGNER', email: 'dana@northfield.example' }];

function entry(id: string, action: string, entityType: string, createdAt: string, userId: string | null, detail: Record<string, unknown> = {}): AuditEntry {
  return { id, userId, action, entityType, entityId: 'e-' + id, detail, createdAt };
}

const entries = [
  entry('1', 'REVIEW_RECORDED', 'mapping', '2026-11-26T10:00:00Z', 'u1', { requirementRef: 'CA-3', kind: 'GAP_CONFIRMED', note: 'No telehealth content.' }),
  entry('2', 'DETERMINATION_SIGNED', 'determination', '2026-11-26T11:00:00Z', 'u1', { jurisdiction: 'CA', outcome: 'DOES_NOT_MEET' }),
  entry('3', 'ASSESSMENT_FINISHED', 'assessment', '2026-11-26T09:00:00Z', null),
  entry('4', 'LOGIN_SUCCEEDED', 'app_user', '2026-11-26T08:00:00Z', 'gone'),
];

describe('audit rows', () => {
  it('resolves actors to names, background work to System', () => {
    expect(auditRows(entries, users).map((r) => r.actor)).toEqual(['Dana Whitfield', 'Dana Whitfield', 'System', 'Former user']);
  });

  it('words the details instead of showing enum values', () => {
    expect(detailSummary(entries[1].detail)).toBe('Jurisdiction: CA · Outcome: Determined not to meet');
    expect(detailSummary(entries[0].detail)).toBe('Requirement: CA-3 · Decision: Gap confirmed · Note: No telehealth content.');
    expect(detailSummary({ kind: 'SYLLABUS', filename: 'coun625.pdf' })).toBe('Kind: syllabus · File: coun625.pdf');
    expect(detailSummary({ unknownKey: 'x', note: '' })).toBe('');
  });

  it('labels actions and record types in plain words', () => {
    const [review, , , login] = auditRows(entries, users);
    expect(review.actionLabel).toBe('Review decision recorded');
    expect(login.entityLabel).toBe('app user');
  });
});

describe('audit filters', () => {
  const rows = auditRows(entries, users);

  it('lists newest first and filters by action and by record type', () => {
    expect(filterRows(rows, { action: null, entityType: null }).map((r) => r.id)).toEqual(['2', '1', '3', '4']);
    expect(filterRows(rows, { action: 'REVIEW_RECORDED', entityType: null }).map((r) => r.id)).toEqual(['1']);
    expect(filterRows(rows, { action: null, entityType: 'assessment' }).map((r) => r.id)).toEqual(['3']);
    expect(filterRows(rows, { action: 'REVIEW_RECORDED', entityType: 'assessment' })).toEqual([]);
  });

  it('offers each value once with a readable label', () => {
    expect(filterOptions(rows, (r) => [r.entityType, r.entityLabel])).toEqual([
      { value: 'app_user', label: 'App user' },
      { value: 'assessment', label: 'Assessment' },
      { value: 'determination', label: 'Determination' },
      { value: 'mapping', label: 'Mapping' },
    ]);
  });
});
