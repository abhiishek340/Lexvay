import { describe, expect, it } from 'vitest';
import { AssessmentView, Determination, Program } from './models';
import { barSegments, jurisdictionRows, overviewCounts } from './overview';

const program: Program = { id: 'p', name: 'P', licenseType: 'L', catalogYear: '2026-27', jurisdictions: ['CA', 'UT'], createdAt: '' };
const assessment: AssessmentView = {
  id: 'a', programId: 'p', status: 'FINISHED', startedAt: null, finishedAt: null, llmModel: 'fake', promptVersion: 'match-v1', createdAt: '',
  jurisdictions: [
    { jurisdiction: 'CA', total: 8, evidenceFound: 5, evidenceMissing: 1, ambiguous: 2, decided: 8 },
    { jurisdiction: 'UT', total: 9, evidenceFound: 7, evidenceMissing: 1, ambiguous: 1, decided: 3 },
  ],
};
const signed: Determination = { id: 'd', programId: 'p', jurisdiction: 'CA', ruleVersionId: 'r', ruleVersionLabel: 'Demo', assessmentId: 'a', outcome: 'MEETS',
  signedBy: 'u', signedByName: 'Dana', signedAt: '', status: 'SIGNED', versionsHash: 'h', courseVersionIds: [] };

describe('overview counts', () => {
  it('sums requirements and waiting-for-review across jurisdictions', () => {
    expect(overviewCounts(program, assessment, [signed])).toEqual({ jurisdictions: 2, requirements: 17, waitingForReview: 6, determinationsSigned: 1 });
  });
  it('handles a program without an assessment', () => {
    expect(overviewCounts(program, null, [])).toEqual({ jurisdictions: 2, requirements: 0, waitingForReview: 0, determinationsSigned: 0 });
  });
});

describe('jurisdiction rows', () => {
  it('reports signed, in-progress and not-assessed states in words', () => {
    const rows = jurisdictionRows(program, assessment, [signed]);
    expect(rows[0]).toMatchObject({ state: 'SIGNED', status: 'Signed', reopened: false });
    expect(rows[1]).toMatchObject({ state: 'IN_REVIEW', status: '3 of 9 reviewed' });
    expect(jurisdictionRows(program, null, [])[0]).toMatchObject({ state: 'NOT_ASSESSED', status: 'Not assessed' });
  });
  it('says when a determination was reopened by a change', () => {
    const reopened: Determination = { ...signed, status: 'REOPENED' };
    const [ca] = jurisdictionRows(program, assessment, [reopened]);
    expect(ca).toMatchObject({ state: 'READY', status: 'Reopened, ready to sign', reopened: true, signed: null });
  });
  it('bar segments add up to 100 percent', () => {
    const total = barSegments(assessment.jurisdictions[0]).reduce((n, s) => n + s.percent, 0);
    expect(Math.round(total)).toBe(100);
  });
});
