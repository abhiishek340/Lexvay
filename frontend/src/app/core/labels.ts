import { DecisionKind, Finding, Outcome } from './models';

/** Plain wording (spec section 9): the two phrases the spec forbids never appear anywhere in the UI. */
export const FINDING_LABEL: Record<Finding, string> = {
  EVIDENCE_FOUND: 'Evidence found',
  EVIDENCE_MISSING: 'Evidence missing',
  AMBIGUOUS: 'Ambiguous',
};

/** A shape per finding so state never depends on colour alone. */
export const FINDING_GLYPH: Record<Finding, string> = {
  EVIDENCE_FOUND: '●',
  EVIDENCE_MISSING: '✕',
  AMBIGUOUS: '◐',
};

export const DECISION_LABEL: Record<DecisionKind, string> = {
  ACCEPTED: 'Accepted',
  EVIDENCE_REQUESTED: 'Evidence requested',
  GAP_CONFIRMED: 'Gap confirmed',
};

export const OUTCOME_LABEL: Record<Outcome, string> = {
  MEETS: 'Determined to meet',
  DOES_NOT_MEET: 'Determined not to meet',
  NOT_DETERMINED: 'No determination made',
};

export const ACTION_LABEL: Record<string, string> = {
  LOGIN_SUCCEEDED: 'Signed in',
  LOGOUT: 'Signed out',
  PROGRAM_CREATED: 'Program created',
  COURSE_CREATED: 'Course created',
  DOCUMENT_UPLOADED: 'Document uploaded',
  DOCUMENT_INGESTED: 'Document text extracted',
  PROGRAM_FACT_RECORDED: 'Program fact recorded',
  RULE_VERSION_CREATED: 'Rule version entered',
  REQUIREMENTS_EXTRACTED: 'Requirements extracted',
  REQUIREMENTS_REPLACED: 'Requirement list replaced',
  REQUIREMENTS_APPROVED: 'Requirement list approved',
  ASSESSMENT_STARTED: 'Assessment started',
  ASSESSMENT_FINISHED: 'Assessment finished',
  REVIEW_RECORDED: 'Review decision recorded',
  DETERMINATION_SIGNED: 'Determination signed',
  JOB_FAILED: 'Background job failed',
  TENANT_CREATED: 'Tenant created',
  USER_CREATED: 'User created',
};

export function actionLabel(action: string): string {
  return ACTION_LABEL[action] ?? action.toLowerCase().replace(/_/g, ' ');
}
