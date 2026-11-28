import { describe, expect, it } from 'vitest';
import { DeterminationLists, DisclosureEntry } from '../../core/models';
import { disclosureSections } from './register-view';

const unsigned: DisclosureEntry = {
  jurisdictionCode: 'TX', jurisdictionName: 'Texas', determinationId: null, signedBy: null, signedByName: null, signedAt: null,
  ruleVersionLabel: null, versionsHash: null, reason: 'NO_CURRENT_SIGNATURE', reopenedDeterminationId: null, reopenedAt: null,
};
const signedEntry = (code: string, name: string): DisclosureEntry => ({
  ...unsigned, jurisdictionCode: code, jurisdictionName: name, determinationId: `d-${code}`, signedBy: 'u', signedByName: 'Dana Whitfield',
  signedAt: '2026-11-26T10:00:00Z', ruleVersionLabel: `Demo rule set ${code}`, versionsHash: 'a'.repeat(64), reason: null,
});
const lists: DeterminationLists = {
  program: { id: 'p', name: 'M.S. Counseling', licenseType: 'L', catalogYear: '2026-27' },
  generatedAt: '2026-11-26T12:00:00Z',
  determinedToMeet: [signedEntry('CA', 'California')],
  determinedNotToMeet: [],
  noDeterminationMade: [
    { ...signedEntry('AZ', 'Arizona'), reason: 'SIGNED_NO_DETERMINATION' },
    { ...unsigned, jurisdictionCode: 'CO', jurisdictionName: 'Colorado', reopenedDeterminationId: 'd-old', reopenedAt: '2026-11-20T09:00:00Z' },
    unsigned,
  ],
};

describe('disclosure sections', () => {
  const sections = disclosureSections(lists);

  it('keeps the three lists in order under the outcome labels used in signing', () => {
    expect(sections.map((s) => s.title)).toEqual(['Determined to meet', 'Determined not to meet', 'No determination made']);
    expect(sections.map((s) => s.outcome)).toEqual(['MEETS', 'DOES_NOT_MEET', 'NOT_DETERMINED']);
    expect(sections.map((s) => s.rows.map((r) => r.code))).toEqual([['CA'], [], ['AZ', 'CO', 'TX']]);
  });
  it('gives every list an empty-state sentence', () => {
    expect(sections[1].rows).toEqual([]);
    expect(sections.every((s) => s.empty.endsWith('.'))).toBe(true);
  });
  it('shows a current signature with its signer, date and rule version and no note', () => {
    expect(sections[0].rows[0]).toEqual({ code: 'CA', name: 'California', signedByName: 'Dana Whitfield', signedAt: '2026-11-26T10:00:00Z',
      ruleVersionLabel: 'Demo rule set CA', note: null, reopenedAt: null });
  });
  it('words a signed "no determination made" and an absent signature differently', () => {
    const [arizona, colorado, texas] = sections[2].rows;
    expect(arizona).toMatchObject({ note: 'Signed as no determination made', signedByName: 'Dana Whitfield', reopenedAt: null });
    expect(colorado).toMatchObject({ note: 'No current signed determination', signedByName: null, reopenedAt: '2026-11-20T09:00:00Z' });
    expect(texas).toMatchObject({ note: 'No current signed determination', reopenedAt: null });
  });
});
