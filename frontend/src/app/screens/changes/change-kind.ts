import { ChangeEvent } from '../../core/models';
import type { IconName } from '../../theme/icons';

export const CHANGE_KIND: Record<ChangeEvent['kind'], { label: string; icon: IconName; action: string }> = {
  RULE_VERSION: { label: 'New rule version', icon: 'phosphorScales', action: 'Re-run assessment' },
  COURSE_VERSION: { label: 'New course version', icon: 'phosphorBookOpen', action: 'Re-run matching' },
};
