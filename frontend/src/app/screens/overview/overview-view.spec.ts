import { describe, expect, it } from 'vitest';
import { AssessmentView, Determination, Program, RuleVersionView, WorkItem } from '../../core/models';
import { NEEDS_YOU_LIMIT, concernsProgram, needsYou, overviewFigures, primaryAction, stateRows } from './overview-view';

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
  id: `d-${jurisdiction}`, programId: 'p1', jurisdiction, ruleVersionId: 'r', ruleVersionLabel: 'CA board rules 2026', assessmentId: 'a', outcome: 'DOES_NOT_MEET',
  signedBy: 'u', signedByName: 'Dana Whitfield', signedAt: '2026-11-26T10:00:00Z', status: 'SIGNED', versionsHash: 'h', courseVersionIds: [], ...over,
});
const version = (jurisdiction: string, label: string, over: Partial<RuleVersionView> = {}): RuleVersionView => ({
  id: `${jurisdiction}-${label}`, jurisdiction, licenseType: 'L', label, sourceUrl: '', legalStatus: 'IN_FORCE', effectiveFrom: '2026-01-01', effectiveTo: null, publishedAt: null, adoptedAt: null, datesBasis: 'Fictional demo data', observedAt: null, queuedObservations: [], supersedesId: null, documentId: 'doc',
  approval: { by: 'u', at: '2026-01-02T00:00:00Z' }, requirementCount: 8, extraction: null, createdAt: '2026-01-01T00:00:00Z', ...over,
});
const versions = [
  version('UT', 'UT rules 2025', { effectiveFrom: '2025-01-01' }), version('UT', 'UT rules 2026'),
  version('CO', 'CO draft', { approval: null }), version('CO', 'CO other license', { licenseType: 'X' }),
];
const names: Record<string, string> = { CA: 'California', UT: 'Utah', CO: 'Colorado', TX: 'Texas', AZ: 'Arizona' };
const name = (code: string) => names[code] ?? code;

describe('state rows', () => {
  const rows = (signer: boolean, determinations: Determination[] = [determination('CA')]) =>
    stateRows(program, assessment, determinations, versions, signer, name);

  it('gives each stage its one action: Report once signed, Sign when ready, Review while requirements wait, none before an assessment', () => {
    const [ca, ut, co, tx] = rows(true);
    expect(ca).toMatchObject({ stage: 'SIGNED', action: { label: 'Report', commands: ['/programs', 'p1', 'report'], queryParams: { jurisdiction: 'CA' } } });
    expect(ut).toMatchObject({ stage: 'READY', action: { label: 'Sign', commands: ['/programs', 'p1', 'review'], queryParams: { jurisdiction: 'UT' } } });
    expect(co).toMatchObject({ stage: 'IN_REVIEW', action: { label: 'Review', queryParams: { jurisdiction: 'CO' } } });
    expect(tx).toMatchObject({ stage: 'NOT_ASSESSED', action: null });
  });

  it('offers Sign only to a signer; anyone else reviews a state that is ready', () => {
    expect(rows(false)[1].action?.label).toBe('Review');
  });

  it('shows progress as decided of total with a meter drawn to scale, and the share of evidence found', () => {
    const [, , co, tx] = rows(true);
    expect(co).toMatchObject({ decided: 3, total: 7, progress: '3 of 7 decided', meter: [{ tone: 'decided', value: 3 }], foundPercent: 57 });
    expect(tx).toMatchObject({ total: 0, progress: 'Not assessed', meter: [], foundPercent: null });
  });

  it('names the signed rule version, else the newest approved one for the license, else none', () => {
    expect(rows(true).map((r) => r.rules)).toEqual(['CA board rules 2026', 'UT rules 2026', null, null]);
  });

  it('says who signed and when, that a signature was reopened, or that there is none', () => {
    const [ca, ut, co] = rows(true, [determination('CA'), determination('UT', { status: 'REOPENED' })]);
    expect(ca).toMatchObject({ determination: 'Signed by Dana Whitfield, 26 Nov', signature: { outcome: 'DOES_NOT_MEET', signer: 'Dana Whitfield' } });
    expect(ut).toMatchObject({ determination: 'Reopened, not signed', signature: null });
    expect(co.determination).toBe('Not signed');
  });
});

describe('figures', () => {
  it('counts what waits for review, the share of evidence found and the signatures', () => {
    expect(overviewFigures(program, assessment, [determination('CA'), determination('UT', { status: 'REOPENED' })]))
      .toEqual({ toReview: 4, foundPercent: 67, signed: 1, targets: 4 });
  });
  it('shows zeros without an assessment', () => {
    expect(overviewFigures(program, null, [])).toEqual({ toReview: 0, foundPercent: 0, signed: 0, targets: 4 });
  });
});

describe('the primary action', () => {
  it('runs the first assessment', () => {
    expect(primaryAction(null, [], true)).toEqual({ kind: 'run' });
  });

  it('signs the first ready state once nothing waits for review, for a signer only', () => {
    const done: AssessmentView = { ...assessment, jurisdictions: assessment.jurisdictions.filter((s) => s.jurisdiction !== 'CO') };
    const twoStates: Program = { ...program, jurisdictions: ['CA', 'UT'] };
    const ready = stateRows(twoStates, done, [determination('CA')], [], true, name);
    expect(primaryAction(done, ready, true)).toMatchObject({ kind: 'sign', label: 'Sign Utah', queryParams: { jurisdiction: 'UT' } });
    expect(primaryAction(done, stateRows(twoStates, done, [determination('CA')], [], false, name), false)).toBeNull();
  });

  it('offers nothing while requirements still wait for review', () => {
    expect(primaryAction(assessment, stateRows(program, assessment, [], [], true, name), true)).toBeNull();
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

  it('shows at most five, what finishes work soonest first, the queue order within a kind', () => {
    const queue = [
      item({ jurisdictionCode: 'CO' }), item({ jurisdictionCode: 'TX', count: 8 }), item({ jurisdictionCode: 'UT', count: 2 }), item({ jurisdictionCode: 'CA', count: 3 }),
      item({ kind: 'EVIDENCE_REQUESTS_OPEN', jurisdictionCode: 'UT', count: 1 }),
      item({ kind: 'READY_TO_SIGN', jurisdictionCode: 'CA', count: 1 }),
      item({ kind: 'REQUIREMENTS_TO_REVIEW', programId: 'p2', jurisdictionCode: 'AZ' }),
    ];
    const rows = needsYou(queue, program, name);
    expect(rows).toHaveLength(NEEDS_YOU_LIMIT);
    expect(rows.map((r) => [r.title, r.detail, r.action])).toEqual([
      ['Ready to sign', 'California', 'Sign'], ['1 evidence request', 'Utah', 'View requests'], ['4 to review', 'Colorado', 'Review'],
      ['8 to review', 'Texas', 'Review'], ['2 to review', 'Utah', 'Review'],
    ]);
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
