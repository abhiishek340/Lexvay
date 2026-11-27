import { RuleVersionStatus } from '../../core/rules';
import type { IconName } from '../../theme/icons';

/** Each status has its own word and shape; the Tag colour only repeats what they say. */
export const RULE_STATUS: Record<RuleVersionStatus, { label: string; icon: IconName; severity: 'info' | 'danger' | 'warn' | 'success' | 'secondary' }> = {
  EXTRACTING: { label: 'Extracting', icon: 'phosphorSpinner', severity: 'info' },
  EXTRACTION_FAILED: { label: 'Extraction failed', icon: 'phosphorWarningCircle', severity: 'danger' },
  NEEDS_APPROVAL: { label: 'Needs approval', icon: 'phosphorCircleDashed', severity: 'warn' },
  APPROVED: { label: 'Approved', icon: 'phosphorSealCheck', severity: 'success' },
  SUPERSEDED: { label: 'Superseded', icon: 'phosphorArchive', severity: 'secondary' },
};
