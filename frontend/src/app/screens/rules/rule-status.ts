import { RuleVersionStatus } from '../../core/rules';

/** Each status in its own words, shown as a Label (ui/label.ts). */
export const RULE_STATUS: Record<RuleVersionStatus, { label: string }> = {
  EXTRACTING: { label: 'Extracting' },
  EXTRACTION_FAILED: { label: 'Extraction failed' },
  NEEDS_APPROVAL: { label: 'Needs approval' },
  APPROVED: { label: 'Approved' },
  SUPERSEDED: { label: 'Superseded' },
};
