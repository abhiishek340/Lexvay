import { DecisionKind, DeterminationStatus, Finding, NumericField, NumericOperator, Outcome, RequirementType, Role } from './models';

/** Plain wording (spec section 9): the two phrases the spec forbids never appear anywhere in the UI. */
export const FINDING_LABEL: Record<Finding, string> = {
  EVIDENCE_FOUND: 'Evidence found',
  EVIDENCE_MISSING: 'Evidence missing',
  AMBIGUOUS: 'Ambiguous',
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

export const STATUS_LABEL: Record<DeterminationStatus, string> = {
  SIGNED: 'Signed',
  REOPENED: 'Reopened',
};

export const ROLE_LABEL: Record<Role, string> = {
  CONTRIBUTOR: 'Contributor',
  REVIEWER: 'Reviewer',
  SIGNER: 'Signer',
  ADMIN: 'Administrator',
};

export const REQUIREMENT_TYPE_LABEL: Record<RequirementType, string> = {
  NUMERIC: 'Numeric',
  SUPERVISED_HOURS: 'Supervised hours',
  SUBJECT: 'Subject',
  ACCREDITATION: 'Accreditation',
  JURISDICTION_SPECIFIC: 'Jurisdiction specific',
};

/** The types whose requirement is a number the rules engine checks (field, operator, value). */
export const NUMERIC_TYPES: readonly RequirementType[] = ['NUMERIC', 'SUPERVISED_HOURS'];

export const NUMERIC_FIELD_LABEL: Record<NumericField, string> = {
  TOTAL_CREDITS: 'Total credits',
  PRACTICUM_HOURS: 'Practicum hours',
  INTERNSHIP_HOURS: 'Internship hours',
};

export const NUMERIC_OPERATORS: readonly NumericOperator[] = ['>=', '>', '=', '<=', '<'];

/** "Internship hours >= 600": how a numeric check is written wherever one is shown. */
export function numericCheck(r: { numericField: NumericField | null; numericOperator: NumericOperator | null; numericValue: number | null }): string | null {
  return r.numericField && r.numericOperator && r.numericValue !== null ? `${NUMERIC_FIELD_LABEL[r.numericField]} ${r.numericOperator} ${r.numericValue}` : null;
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
