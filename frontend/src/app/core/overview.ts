import { AssessmentView, Determination, JurisdictionSummary, Program } from './models';

export interface OverviewCounts { jurisdictions: number; requirements: number; waitingForReview: number; determinationsSigned: number; }

/** Where a jurisdiction's determination stands. */
export type JurisdictionState = 'SIGNED' | 'READY' | 'IN_REVIEW' | 'NOT_ASSESSED';

export interface JurisdictionRow {
  code: string;
  summary: JurisdictionSummary | null;
  state: JurisdictionState;
  signed: Determination | null;
  /** An earlier signature was reopened by a change and none is current. */
  reopened: boolean;
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
    const reopened = !signed && determinations.some((d) => d.jurisdiction === code && d.status === 'REOPENED');
    const state: JurisdictionState = signed ? 'SIGNED' : !summary ? 'NOT_ASSESSED' : summary.decided === summary.total ? 'READY' : 'IN_REVIEW';
    return { code, summary, state, signed, reopened };
  });
}
