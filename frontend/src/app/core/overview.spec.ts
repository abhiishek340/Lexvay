import { describe, expect, it } from 'vitest';
import { AssessmentView, Determination, Program } from './models';
import { jurisdictionRows, overviewCounts } from './overview';

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
  it('reports signed, in-review and not-assessed states', () => {
    const rows = jurisdictionRows(program, assessment, [signed]);
    expect(rows[0]).toMatchObject({ state: 'SIGNED', signed, reopened: false });
    expect(rows[1]).toMatchObject({ state: 'IN_REVIEW', signed: null, reopened: false });
    expect(jurisdictionRows(program, null, [])[0]).toMatchObject({ state: 'NOT_ASSESSED', summary: null });
  });
  it('is ready to sign once every requirement is decided, and says when a signature was reopened by a change', () => {
    const reopened: Determination = { ...signed, status: 'REOPENED' };
    const [ca] = jurisdictionRows(program, assessment, [reopened]);
    expect(ca).toMatchObject({ state: 'READY', signed: null, reopened: true });
    expect(jurisdictionRows(program, assessment, [reopened, signed])[0]).toMatchObject({ state: 'SIGNED', reopened: false });
  });
});
