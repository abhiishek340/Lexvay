import { ChangeEvent } from '../../core/models';

export const CHANGE_KIND: Record<ChangeEvent['kind'], { label: string; action: string }> = {
  RULE_VERSION: { label: 'New rule version', action: 'Re-run assessment' },
  COURSE_VERSION: { label: 'New course version', action: 'Re-run matching' },
};
