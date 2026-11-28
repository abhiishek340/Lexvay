import { describe, expect, it } from 'vitest';
import { AssessmentView, Determination, Program, WorkItem } from '../../core/models';
import { NEEDS_YOU_LIMIT, concernsProgram, heroStats, mapStates, needsYou, stateCards } from './overview-view';

const program: Program = { id: 'p1', name: 'M.S. Counseling', licenseType: 'L', catalogYear: '2026-27', jurisdictions: ['CA', 'UT', 'CO', 'TX'], createdAt: '' };
const assessment: AssessmentView = {
  id: 'a', programId: 'p1', status: 'FINISHED', startedAt: null, finishedAt: null, llmModel: 'fake', promptVersion: 'match-v1', assessmentAsOf: '2026-11-27', createdAt: '',
  jurisdictions: [
    { jurisdiction: 'CA', total: 8, evidenceFound: 5, evidenceMissing: 1, ambiguous: 2, decided: 8 },
    { jurisdiction: 'UT', total: 9, evidenceFound: 7, evidenceMissing: 1, ambiguous: 1, decided: 9 },
    { jurisdiction: 'CO', total: 7, evidenceFound: 4, evidenceMissing: 2, ambiguous: 1, decided: 3 },
  ],
};
const determination = (jurisdiction: string, over: Partial<Determination> = {}): Determination => ({
  id: `d-${jurisdiction}`, programId: 'p1', jurisdiction, ruleVersionId: 'r', ruleVersionLabel: 'Demo', assessmentId: 'a', outcome: 'DOES_NOT_MEET',
  signedBy: 'u', signedByName: 'Dana Whitfield', signedAt: '2026-11-26T10:00:00Z', status: 'SIGNED', versionsHash: 'h', courseVersionIds: [], ...over,
});
const names: Record<string, string> = { CA: 'California', UT: 'Utah', CO: 'Colorado', TX: 'Texas', AZ: 'Arizona' };
const name = (code: string) => names[code] ?? code;

describe('state cards', () => {
  const cards = (signer: boolean, determinations: Determination[] = [determination('CA')]) => stateCards(program, assessment, determinations, signer, name);

  it('gives each stage its one action: View report once signed, Sign when ready, Review while requirements wait, none before an assessment', () => {
    const [ca, ut, co, tx] = cards(true);
    expect(ca).toMatchObject({ stage: 'SIGNED', tile: 'DOES_NOT_MEET', status: 'Determined not to meet', tone: 'missing', icon: 'phosphorXCircle',
      action: { label: 'View report', commands: ['/programs', 'p1', 'report'], queryParams: { jurisdiction: 'CA' } } });
    expect(ut).toMatchObject({ stage: 'READY', tile: 'IN_REVIEW', status: 'Ready to sign', tone: 'accent',
      action: { label: 'Sign', commands: ['/programs', 'p1', 'review'], queryParams: { jurisdiction: 'UT' } } });
    expect(co).toMatchObject({ stage: 'IN_REVIEW', status: '4 to review', reviewed: 3, total: 7, action: { label: 'Review', queryParams: { jurisdiction: 'CO' } } });
    expect(tx).toMatchObject({ stage: 'NOT_ASSESSED', status: 'Not assessed', total: 0, action: null });
  });

  it('offers Sign only to a signer; anyone else reviews a state that is ready', () => {
    expect(cards(false)[1]).toMatchObject({ status: 'Ready to sign', action: { label: 'Review' } });
  });

  it('says when a signature was reopened by a change', () => {
    const reopened = cards(true, [determination('UT', { status: 'REOPENED' }), determination('CO', { status: 'REOPENED' })]);
    expect(reopened[1]).toMatchObject({ stage: 'READY', status: 'Reopened, ready to sign' });
    expect(reopened[2]).toMatchObject({ stage: 'IN_REVIEW', status: 'Reopened, 4 to review', icon: 'phosphorArrowCounterClockwise' });
  });

  it('names the seal colour for a signed "no determination made"', () => {
    expect(cards(true, [determination('CA', { outcome: 'NOT_DETERMINED' })])[0]).toMatchObject({ tile: 'NOT_DETERMINED', tone: 'seal', status: 'No determination made' });
  });

  it('rings the findings in the fixed order and names them', () => {
    const [ca] = cards(true);
    expect(ca.segments.map((s) => [s.tone, s.value])).toEqual([['found', 5], ['ambiguous', 2], ['missing', 1], ['neutral', 0]]);
    expect(ca.ringLabel).toBe('5 evidence found, 2 ambiguous, 1 evidence missing');
  });

  it('gives the map the same facts and actions as the cards', () => {
    const states = mapStates(cards(true));
    expect(states.map((s) => [s.code, s.tile, s.reviewed, s.total, s.action?.label ?? null])).toEqual([
      ['CA', 'DOES_NOT_MEET', 8, 8, 'View report'], ['UT', 'IN_REVIEW', 9, 9, 'Sign'], ['CO', 'IN_REVIEW', 3, 7, 'Review'], ['TX', 'IN_REVIEW', 0, 0, null],
    ]);
  });
});

describe('hero stats', () => {
  it('counts what waits for review, the share of evidence found and the signatures', () => {
    expect(heroStats(program, assessment, [determination('CA'), determination('UT', { status: 'REOPENED' })])).toMatchObject({
      toReview: 4, reviewed: 20, requirements: 24, foundPercent: 67, signed: 1, targets: 4,
      findingsLabel: '16 evidence found, 4 ambiguous, 4 evidence missing, of 24 requirements',
    });
  });
  it('shows zeros without an assessment', () => {
    expect(heroStats(program, null, [])).toMatchObject({ toReview: 0, requirements: 0, foundPercent: 0, signed: 0, targets: 4 });
  });
});

describe('needs you', () => {
  const item = (over: Partial<WorkItem>): WorkItem => ({
    kind: 'REQUIREMENTS_TO_REVIEW', programId: 'p1', programName: 'M.S. Counseling', jurisdictionCode: 'CO', count: 4, oldestAt: '2026-11-01T00:00:00Z', ids: {}, ...over,
  });

  it('keeps the items of this program: its own, changes that touch it, rule versions of its states', () => {
    expect(concernsProgram(item({}), program)).toBe(true);
    expect(concernsProgram(item({ programId: 'p2' }), program)).toBe(false);
    expect(concernsProgram(item({ kind: 'CHANGES_OPEN', programId: null, jurisdictionCode: null, ids: { programIds: ['p2', 'p1'] } }), program)).toBe(true);
    expect(concernsProgram(item({ kind: 'CHANGES_OPEN', programId: null, jurisdictionCode: null, ids: { programIds: ['p2'] } }), program)).toBe(false);
    expect(concernsProgram(item({ kind: 'RULE_VERSIONS_TO_APPROVE', programId: null, jurisdictionCode: 'UT' }), program)).toBe(true);
    expect(concernsProgram(item({ kind: 'EXTRACTION_FAILED', programId: null, jurisdictionCode: 'AZ' }), program)).toBe(false);
  });

  it('shows at most three, what finishes work soonest first, the queue order within a kind', () => {
    const queue = [
      item({ jurisdictionCode: 'CO' }), item({ jurisdictionCode: 'TX', count: 8 }),
      item({ kind: 'EVIDENCE_REQUESTS_OPEN', jurisdictionCode: 'UT', count: 1 }),
      item({ kind: 'READY_TO_SIGN', jurisdictionCode: 'CA', count: 1 }),
      item({ kind: 'REQUIREMENTS_TO_REVIEW', programId: 'p2', jurisdictionCode: 'AZ' }),
    ];
    const rows = needsYou(queue, program, name);
    expect(rows).toHaveLength(NEEDS_YOU_LIMIT);
    expect(rows.map((r) => [r.title, r.detail, r.action])).toEqual([['Ready to sign', 'California', 'Sign'], ['1 evidence request', 'Utah', 'View requests'], ['4 to review', 'Colorado', 'Review']]);
    expect(rows[0]).toMatchObject({ commands: ['/programs', 'p1', 'review'], queryParams: { jurisdiction: 'CA' }, run: 'navigate' });
  });

  it('runs a failed assessment again in place and names open changes by kind', () => {
    const rows = needsYou([
      item({ kind: 'CHANGES_OPEN', programId: null, programName: null, jurisdictionCode: null, count: 2, ids: { changeKind: 'RULE_VERSION', programIds: ['p1'] } }),
      item({ kind: 'ASSESSMENTS_FAILED', jurisdictionCode: null, count: 1 }),
    ], program, name);
    expect(rows.map((r) => [r.title, r.detail, r.action, r.run])).toEqual([
      ['1 failed assessment', '', 'Run again', 'assessment'], ['2 open changes', 'New rule versions', 'View changes', 'navigate'],
    ]);
    expect(rows[1].commands).toEqual(['/programs', 'p1', 'changes']);
  });

  it('is empty when nothing concerns the program', () => {
    expect(needsYou([item({ programId: 'p2' })], program, name)).toEqual([]);
  });
});
