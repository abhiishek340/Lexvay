import { AssessmentView, Determination, JurisdictionSummary, Program } from './models';

export interface OverviewCounts { jurisdictions: number; requirements: number; waitingForReview: number; determinationsSigned: number; }

export interface JurisdictionRow {
  code: string;
  summary: JurisdictionSummary | null;
  status: string;
  signed: Determination | null;
}

/** The four counts on the Program overview, computed from the latest assessment and the determinations. */
export function overviewCounts(program: Program, assessment: AssessmentView | null, determinations: Determination[]): OverviewCounts {
  const summaries = assessment?.jurisdictions ?? [];
  const requirements = summaries.reduce((n, s) => n + s.total, 0);
  const decided = summaries.reduce((n, s) => n + s.decided, 0);
  return {
    jurisdictions: program.jurisdictions.length,
    requirements,
    waitingForReview: requirements - decided,
    determinationsSigned: determinations.filter((d) => d.status === 'SIGNED').length,
  };
}

export function jurisdictionRows(program: Program, assessment: AssessmentView | null, determinations: Determination[]): JurisdictionRow[] {
  return program.jurisdictions.map((code) => {
    const summary = assessment?.jurisdictions.find((s) => s.jurisdiction === code) ?? null;
    const signed = determinations.find((d) => d.jurisdiction === code && d.status === 'SIGNED') ?? null;
    const reopened = determinations.find((d) => d.jurisdiction === code && d.status === 'REOPENED') ?? null;
    let status: string;
    if (signed) {
      status = 'Signed';
    } else if (!summary) {
      status = 'Not assessed';
    } else if (summary.decided === summary.total) {
      status = reopened ? 'Reopened, ready to sign' : 'Ready to sign';
    } else {
      status = `${summary.decided} of ${summary.total} reviewed`;
    }
    return { code, summary, status, signed };
  });
}

/** Widths for the stacked findings bar, in percent. */
export function barSegments(summary: JurisdictionSummary): { finding: 'EVIDENCE_FOUND' | 'AMBIGUOUS' | 'EVIDENCE_MISSING'; count: number; percent: number }[] {
  const total = Math.max(summary.total, 1);
  return [
    { finding: 'EVIDENCE_FOUND', count: summary.evidenceFound, percent: (summary.evidenceFound / total) * 100 },
    { finding: 'AMBIGUOUS', count: summary.ambiguous, percent: (summary.ambiguous / total) * 100 },
    { finding: 'EVIDENCE_MISSING', count: summary.evidenceMissing, percent: (summary.evidenceMissing / total) * 100 },
  ];
}
