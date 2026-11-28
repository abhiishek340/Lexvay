import { DiffStatus } from '../../core/models';

/** Each kind of change in its own word, in Compare's Status column; the diff beside it carries the underlines. */
export const DIFF_STATUS: Record<DiffStatus, { label: string }> = {
  CHANGED: { label: 'Changed' },
  ADDED: { label: 'Added' },
  REMOVED: { label: 'Removed' },
  UNCHANGED: { label: 'Unchanged' },
};
