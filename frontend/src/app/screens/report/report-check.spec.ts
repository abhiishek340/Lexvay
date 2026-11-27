import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { ReportCheck, ReportCheckMatch } from '../../core/models';
import { checkProblem, checkView, fileProblem } from './report-check';

const SHA = '3f9a1c0be47d2e85a6b1f0c9d8e7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9';
const names = (code: string): string => ({ CA: 'California' })[code] ?? code;
const signed: ReportCheckMatch = {
  reportId: 'r', generatedAt: '2026-11-26T15:04:00Z', determinationId: 'd', programId: 'p', programName: 'M.S. Clinical Mental Health Counseling',
  jurisdictionCode: 'CA', ruleVersionLabel: 'Demo rule set California 2026', outcome: 'DOES_NOT_MEET', signedByName: 'Dana Whitfield',
  signedAt: '2026-11-25T10:00:00Z', determinationStatus: 'SIGNED', reopenedAt: null, versionsHash: 'a'.repeat(64),
};
const matched = (m: ReportCheckMatch): ReportCheck => ({ match: true, sha256: SHA, matchDetails: m });

describe('report check result views', () => {
  it('shows a match with a signed determination as the seal, the generation date and three facts', () => {
    const view = checkView(matched(signed), names);
    expect(view).toEqual({
      kind: 'signed',
      headline: 'Matches the report generated 26 November 2026',
      place: 'California · M.S. Clinical Mental Health Counseling',
      signature: 'Signed by Dana Whitfield, 25 November 2026',
      ruleVersion: 'Demo rule set California 2026',
      seal: { outcome: 'DOES_NOT_MEET', signer: 'Dana Whitfield', signedAt: '2026-11-25T10:00:00Z' },
      report: { programId: 'p', jurisdiction: 'CA' },
      fingerprint: { sha256: SHA, short: '3f9a1c0be47d2e85' },
    });
  });

  it('shows a match whose determination was reopened with the date and no seal or signature', () => {
    const view = checkView(matched({ ...signed, determinationStatus: 'REOPENED', reopenedAt: '2026-11-27T09:30:00Z' }), names);
    expect(view).toEqual({
      kind: 'reopened',
      headline: 'Matches, but this determination was reopened on 27 November 2026',
      place: 'California · M.S. Clinical Mental Health Counseling',
      fingerprint: { sha256: SHA, short: '3f9a1c0be47d2e85' },
    });
    expect(view).not.toHaveProperty('seal');
  });

  it('shows no match as one fact about the workspace, with the fingerprint of the file', () => {
    expect(checkView({ match: false, sha256: SHA, matchDetails: null }, names)).toEqual({
      kind: 'none', headline: 'No report in this workspace has exactly this content', fingerprint: { sha256: SHA, short: '3f9a1c0be47d2e85' },
    });
  });

  it('never words a result as a verdict on the determination', () => {
    const views = [checkView(matched(signed), names), checkView(matched({ ...signed, determinationStatus: 'REOPENED', reopenedAt: signed.signedAt }), names),
      checkView({ match: false, sha256: SHA, matchDetails: null }, names)];
    for (const text of views.map((v) => JSON.stringify(v).toLowerCase())) {
      for (const word of ['compliant', 'meets requirements', 'valid', 'authentic']) { expect(text).not.toContain(word); }
    }
  });
});

describe('report check errors', () => {
  it('refuses a file that is not a PDF before sending it', () => {
    expect(fileProblem({ name: 'report.pdf', type: 'application/pdf' })).toBeNull();
    expect(fileProblem({ name: 'REPORT.PDF', type: '' })).toBeNull();
    expect(fileProblem({ name: 'report.docx', type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })).toBe('Not a PDF file');
    expect(fileProblem({ name: 'report.pdf.txt', type: 'text/plain' })).toBe('Not a PDF file');
  });

  it('says a file over the upload limit is too large, and otherwise gives the server its word', () => {
    expect(checkProblem(new HttpErrorResponse({ status: 413, error: { detail: 'Maximum upload size exceeded' } }))).toBe('The file is larger than the upload limit');
    expect(checkProblem(new HttpErrorResponse({ status: 400, error: { detail: 'Not a PDF file' } }))).toBe('Not a PDF file');
    expect(checkProblem(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }))).toBe('The file could not be checked. Try again.');
  });
});
