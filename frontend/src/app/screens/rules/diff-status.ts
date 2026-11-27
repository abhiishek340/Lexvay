import { DiffStatus } from '../../core/models';
import type { IconName } from '../../theme/icons';

export const DIFF_STATUS: Record<DiffStatus, { label: string; icon: IconName; severity: 'success' | 'danger' | 'warn' | 'secondary' }> = {
  CHANGED: { label: 'Changed', icon: 'phosphorNotePencil', severity: 'warn' },
  ADDED: { label: 'Added', icon: 'phosphorPlusCircle', severity: 'success' },
  REMOVED: { label: 'Removed', icon: 'phosphorMinusCircle', severity: 'danger' },
  UNCHANGED: { label: 'Unchanged', icon: 'phosphorEquals', severity: 'secondary' },
};
