import { describe, expect, it } from 'vitest';
import { ChangeEvent, Determination } from '../../core/models';
import { affects, changeCard, changeCards, versionPills } from './change-card';

const names = (code: string): string => ({ CO: 'Colorado', CA: 'California' }[code] ?? code);

const rule: ChangeEvent = {
  id: 'c1', kind: 'RULE_VERSION', oldId: 'r1', newId: 'r2', status: 'OPEN', createdAt: '2026-11-26T10:00:00Z',
  jurisdiction: 'CO', oldLabel: 'Demo rule set - Colorado (fictional) 2026', newLabel: 'Demo rule set - Colorado (fictional) 2026 revision 2',
  newApproved: false, courseCode: null, oldCatalogYear: null, newCatalogYear: null, newVersionHasDocument: false, reopenedDeterminationIds: ['d1'],
};
const course: ChangeEvent = {
  ...rule, id: 'c2', kind: 'COURSE_VERSION', status: 'HANDLED', createdAt: '2026-11-27T09:00:00Z', jurisdiction: null, oldLabel: null, newLabel: null,
  courseCode: 'COUN 625', oldCatalogYear: '2026-27', newCatalogYear: '2027-28', newVersionHasDocument: true, reopenedDeterminationIds: [],
};
const determination = (id: string, jurisdiction: string): Determination => ({
  id, programId: 'p', jurisdiction, ruleVersionId: 'r1', ruleVersionLabel: 'x', assessmentId: 'a', outcome: 'MEETS', signedBy: 'u',
  signedByName: 'Dana Whitfield', signedAt: '2026-11-20T10:00:00Z', status: 'REOPENED', versionsHash: 'h', courseVersionIds: [],
});

describe('version pills', () => {
  it('keep what differs, with the last shared word when one name extends the other', () => {
    expect(versionPills('Demo rule set - Colorado (fictional) 2026', 'Demo rule set - Colorado (fictional) 2026 revision 2'))
      .toEqual({ from: '2026', to: '2026 revision 2', added: 'revision 2' });
    expect(versionPills('Colorado rules 2025', 'Colorado rules 2026')).toEqual({ from: '2025', to: '2026', added: '2026' });
  });

  it('drop a separator left at the start and keep names that share nothing whole', () => {
    expect(versionPills('Rules 2025', 'Rules - 2026 draft')).toEqual({ from: '2025', to: '2026 draft', added: '2026 draft' });
    expect(versionPills('Old board rules', 'New board rules')).toEqual({ from: 'Old board rules', to: 'New board rules', added: 'New board rules' });
    expect(versionPills('Same', 'Same')).toEqual({ from: 'Same', to: 'Same', added: '' });
  });
});

describe('change card', () => {
  it('titles a rule change by state and what is new, with one action and its blocker', () => {
    const card = changeCard(rule, names, [determination('d1', 'CO')]);
    expect(card).toMatchObject({
      title: 'Colorado rules, revision 2', from: '2026', to: '2026 revision 2', icon: 'phosphorScales', open: true,
      action: 'Re-run assessment', blocker: 'Approve the new requirement list first.', affects: 'Colorado', versionNoun: 'Rule version',
      fromFull: 'Demo rule set - Colorado (fictional) 2026', toFull: 'Demo rule set - Colorado (fictional) 2026 revision 2',
    });
    expect(changeCard({ ...rule, newApproved: true }, names, []).blocker).toBeNull();
  });

  it('titles a course change by course, with the catalog years as pills', () => {
    expect(changeCard(course, names, [])).toMatchObject({
      title: 'COUN 625, new catalog year', from: '2026-27', to: '2027-28', icon: 'phosphorBookOpen', open: false,
      action: 'Re-run matching', blocker: null, affects: 'No signed determination', versionNoun: 'Catalog year',
    });
  });

  it('names reopened determinations of this program and counts the others', () => {
    const here = [determination('d1', 'CO'), determination('d2', 'CA')];
    expect(affects(['d2', 'd1'], names, here)).toBe('California, Colorado');
    expect(affects(['d1', 'x'], names, here)).toBe('Colorado, 1 in another program');
    expect(affects(['x', 'y'], names, here)).toBe('2 in other programs');
  });

  it('lists the newest change first', () => {
    expect(changeCards([rule, course], names, []).map((c) => c.id)).toEqual(['c2', 'c1']);
  });
});
