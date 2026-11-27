import { DECISION_LABEL, FINDING_LABEL } from '../core/labels';
import { DecisionKind, Finding } from '../core/models';
import type { IconName } from '../theme/icons';

export type StatusTone = 'found' | 'ambiguous' | 'missing' | 'muted';
export interface RequirementStatus { readonly icon: IconName; readonly tone: StatusTone; readonly label: string }

/** The proposed finding, outlined: nobody has decided yet. */
const PROPOSED: Record<Finding, { icon: IconName; tone: StatusTone }> = {
  EVIDENCE_FOUND: { icon: 'phosphorCheckCircle', tone: 'found' },
  AMBIGUOUS: { icon: 'phosphorQuestion', tone: 'ambiguous' },
  EVIDENCE_MISSING: { icon: 'phosphorXCircle', tone: 'missing' },
};

/** The person's decision, filled: it has landed. */
const DECIDED: Record<DecisionKind, { icon: IconName; tone: StatusTone }> = {
  ACCEPTED: { icon: 'phosphorCheckCircleFill', tone: 'found' },
  EVIDENCE_REQUESTED: { icon: 'phosphorPaperPlaneTiltFill', tone: 'ambiguous' },
  GAP_CONFIRMED: { icon: 'phosphorXCircleFill', tone: 'missing' },
};

/**
 * One status for a requirement row, combining the proposed finding and the current decision (DESIGN.md
 * "StatusIcon"): an outlined finding icon while undecided ("Evidence found, not reviewed"), a filled decision icon
 * once decided ("Evidence found, accepted"), and "outdated" when a change made the decision stale.
 */
export function requirementStatus(finding: Finding, decision: DecisionKind | null, stale: boolean): RequirementStatus {
  const found = FINDING_LABEL[finding];
  if (stale) {
    return { icon: 'phosphorArrowsClockwise', tone: 'muted', label: `${found}, outdated by a change` };
  }
  if (decision === null) {
    return { ...PROPOSED[finding], label: `${found}, not reviewed` };
  }
  return { ...DECIDED[decision], label: `${found}, ${DECISION_LABEL[decision].toLowerCase()}` };
}
