import type { IconName } from '../theme/icons';
import { ChangeEvent, DecisionKind, DeterminationStatus, Finding, Outcome, Role } from './models';

/** Plain wording (spec section 9): the two phrases the spec forbids never appear anywhere in the UI. */
export const FINDING_LABEL: Record<Finding, string> = {
  EVIDENCE_FOUND: 'Evidence found',
  EVIDENCE_MISSING: 'Evidence missing',
  AMBIGUOUS: 'Ambiguous',
};

/** A distinct shape per finding (check, question mark, cross) so state never depends on colour alone. */
export const FINDING_ICON: Record<Finding, IconName> = {
  EVIDENCE_FOUND: 'phosphorCheckCircleBold',
  AMBIGUOUS: 'phosphorQuestionBold',
  EVIDENCE_MISSING: 'phosphorXCircleBold',
};

export const DECISION_LABEL: Record<DecisionKind, string> = {
  ACCEPTED: 'Accepted',
  EVIDENCE_REQUESTED: 'Evidence requested',
  GAP_CONFIRMED: 'Gap confirmed',
};

export const DECISION_ICON: Record<DecisionKind, IconName> = {
  ACCEPTED: 'phosphorSealCheck',
  EVIDENCE_REQUESTED: 'phosphorPaperPlaneTilt',
  GAP_CONFIRMED: 'phosphorSealWarning',
};

export const OUTCOME_LABEL: Record<Outcome, string> = {
  MEETS: 'Determined to meet',
  DOES_NOT_MEET: 'Determined not to meet',
  NOT_DETERMINED: 'No determination made',
};

/** A distinct shape per signed outcome, so the outcome never depends on colour alone. */
export const OUTCOME_ICON: Record<Outcome, IconName> = {
  MEETS: 'phosphorCheckCircle',
  DOES_NOT_MEET: 'phosphorXCircle',
  NOT_DETERMINED: 'phosphorMinusCircle',
};

export const STATUS_LABEL: Record<DeterminationStatus, string> = {
  SIGNED: 'Signed',
  REOPENED: 'Reopened',
};

export const STATUS_ICON: Record<DeterminationStatus, IconName> = {
  SIGNED: 'phosphorSealCheck',
  REOPENED: 'phosphorArrowCounterClockwise',
};

export const ROLE_LABEL: Record<Role, string> = {
  CONTRIBUTOR: 'Contributor',
  REVIEWER: 'Reviewer',
  SIGNER: 'Signer',
  ADMIN: 'Administrator',
};

export const CHANGE_KIND: Record<ChangeEvent['kind'], { label: string; icon: IconName; action: string }> = {
  RULE_VERSION: { label: 'New rule version', icon: 'phosphorScales', action: 'Re-run assessment' },
  COURSE_VERSION: { label: 'New course version', icon: 'phosphorBookOpen', action: 'Re-run matching' },
};

export const ACTION_LABEL: Record<string, string> = {
  LOGIN_SUCCEEDED: 'Signed in',
  LOGOUT: 'Signed out',
  PROGRAM_CREATED: 'Program created',
  COURSE_CREATED: 'Course created',
  COURSE_VERSION_ADDED: 'Course version added',
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
  DETERMINATION_REOPENED: 'Determination reopened',
  REGISTER_EXPORTED: 'Determination register exported',
  CHANGE_EVENT_CREATED: 'Change recorded',
  CHANGE_RERUN_REQUESTED: 'Re-run requested',
  CHANGE_RERUN: 'Change re-run finished',
  REPORT_REQUESTED: 'Evidence report requested',
  REPORT_GENERATED: 'Evidence report generated',
  JOB_FAILED: 'Background job failed',
  TENANT_CREATED: 'Tenant created',
  USER_CREATED: 'User created',
};

export function actionLabel(action: string): string {
  return ACTION_LABEL[action] ?? humanize(action);
}

/** "CLINICAL_MENTAL_HEALTH_COUNSELING" becomes "Clinical mental health counseling license". */
export function licenseLabel(program: { licenseType: string }): string {
  const words = humanize(program.licenseType);
  return `${words.charAt(0).toUpperCase()}${words.slice(1)} license`;
}

/** "COURSE_VERSION" and "courseVersion" both become "course version". */
export function humanize(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').toLowerCase();
}
