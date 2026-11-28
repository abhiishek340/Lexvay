import { RuleVersionStatus } from '../../core/rules';
import type { IconName } from '../../theme/icons';

export type RuleStatusTone = 'accent' | 'missing' | 'ambiguous' | 'found' | 'muted';

/**
 * Each status has its own word and shape; colour only repeats them. `tone` colours the icon where the status is one
 * icon and a word (the version list); the version header shows the word as a Label.
 */
export const RULE_STATUS: Record<RuleVersionStatus, { label: string; icon: IconName; tone: RuleStatusTone }> = {
  EXTRACTING: { label: 'Extracting', icon: 'phosphorSpinner', tone: 'accent' },
  EXTRACTION_FAILED: { label: 'Extraction failed', icon: 'phosphorWarningCircle', tone: 'missing' },
  NEEDS_APPROVAL: { label: 'Needs approval', icon: 'phosphorCircleDashed', tone: 'ambiguous' },
  APPROVED: { label: 'Approved', icon: 'phosphorSealCheck', tone: 'found' },
  SUPERSEDED: { label: 'Superseded', icon: 'phosphorArchive', tone: 'muted' },
};
