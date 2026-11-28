import { DECISION_LABEL, FINDING_LABEL } from '../core/labels';
import { DecisionKind, Finding } from '../core/models';
import { Status } from './status-mark';

/** A requirement's one mark and the words beside it. */
export interface RequirementStatus { readonly status: Status; readonly label: string }

/**
 * One status for a requirement row, combining the proposed finding and the current decision: the finding's mark while
 * nobody has decided ("Evidence found, not reviewed"), the decision's mark once someone has ("Evidence found,
 * accepted"), and the outdated mark when a change made the decision stale. The words always name both.
 */
export function requirementStatus(finding: Finding, decision: DecisionKind | null, stale: boolean): RequirementStatus {
  const found = FINDING_LABEL[finding];
  if (stale) {
    return { status: 'OUTDATED', label: `${found}, outdated by a change` };
  }
  if (decision === null) {
    return { status: finding, label: `${found}, not reviewed` };
  }
  return { status: decision, label: `${found}, ${DECISION_LABEL[decision].toLowerCase()}` };
}

/** Where a requirement stands in review on its own: decided (with what), outdated by a change, or not reviewed. */
export function decisionStatus(decision: DecisionKind | null, stale: boolean): RequirementStatus {
  if (stale) {
    return { status: 'OUTDATED', label: decision ? `Outdated: ${DECISION_LABEL[decision].toLowerCase()}` : 'Outdated' };
  }
  return decision ? { status: decision, label: DECISION_LABEL[decision] } : { status: 'NOT_REVIEWED', label: 'Not reviewed' };
}
