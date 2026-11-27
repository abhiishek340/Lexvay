import { RuleVersionStatus } from '../../core/rules';
import type { IconName } from '../../theme/icons';

export type RuleStatusTone = 'accent' | 'missing' | 'ambiguous' | 'found' | 'muted';

/**
 * Each status has its own word and shape; colour only repeats them. `tone` colours the icon where the status is one
 * icon and a word (the version list, StatusIcon style); `severity` is the same colour as a Tag (the version header).
 */
export const RULE_STATUS: Record<RuleVersionStatus, { label: string; icon: IconName; tone: RuleStatusTone; severity: 'info' | 'danger' | 'warn' | 'success' | 'secondary' }> = {
  EXTRACTING: { label: 'Extracting', icon: 'phosphorSpinner', tone: 'accent', severity: 'info' },
  EXTRACTION_FAILED: { label: 'Extraction failed', icon: 'phosphorWarningCircle', tone: 'missing', severity: 'danger' },
  NEEDS_APPROVAL: { label: 'Needs approval', icon: 'phosphorCircleDashed', tone: 'ambiguous', severity: 'warn' },
  APPROVED: { label: 'Approved', icon: 'phosphorSealCheck', tone: 'found', severity: 'success' },
  SUPERSEDED: { label: 'Superseded', icon: 'phosphorArchive', tone: 'muted', severity: 'secondary' },
};
