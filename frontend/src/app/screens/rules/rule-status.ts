import { RuleVersionStatus } from '../../core/rules';
import { MarkShape, STATUS_MARK, Status } from '../../ui/status-mark';

/**
 * Each status as a mark and its own word (DESIGN.md "Status marks": a mark plus a word, never the mark alone). The mark
 * lends its shape: an approved list is accepted (filled disc), one waiting for a reviewer is not reviewed (outline
 * ring), a running extraction waits on the model (ring with a centre dot), a failed one leaves a reviewer to act
 * (filled square), and a superseded one is outdated (dashed ring). The word always says which.
 */
export const RULE_STATUS: Record<RuleVersionStatus, { label: string; mark: Status }> = {
  EXTRACTING: { label: 'Extracting', mark: 'EVIDENCE_REQUESTED' },
  EXTRACTION_FAILED: { label: 'Extraction failed', mark: 'EVIDENCE_MISSING' },
  NEEDS_APPROVAL: { label: 'Needs approval', mark: 'NOT_REVIEWED' },
  APPROVED: { label: 'Approved', mark: 'ACCEPTED' },
  SUPERSEDED: { label: 'Superseded', mark: 'OUTDATED' },
};

/** The shape a status draws; no two statuses share one. */
export function ruleStatusShape(status: RuleVersionStatus): MarkShape {
  return STATUS_MARK[RULE_STATUS[status].mark].shape;
}
