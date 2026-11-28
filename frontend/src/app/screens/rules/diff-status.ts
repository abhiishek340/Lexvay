import { DiffStatus } from '../../core/models';
import type { IconName } from '../../theme/icons';

/** Each kind of change in its own words and shape: the shape heads the summary, the word labels the row. */
export const DIFF_STATUS: Record<DiffStatus, { label: string; icon: IconName }> = {
  CHANGED: { label: 'Changed', icon: 'phosphorNotePencil' },
  ADDED: { label: 'Added', icon: 'phosphorPlusCircle' },
  REMOVED: { label: 'Removed', icon: 'phosphorMinusCircle' },
  UNCHANGED: { label: 'Unchanged', icon: 'phosphorEquals' },
};
