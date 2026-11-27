import { ChangeEvent } from '../../core/models';

/** Why a change cannot be re-run yet, shown as helper text next to the disabled action; null when it can. */
export function rerunBlocker(e: ChangeEvent): string | null {
  if (e.status !== 'OPEN') { return null; }
  if (e.kind === 'RULE_VERSION' && !e.newApproved) { return 'Approve the new requirement list first.'; }
  if (e.kind === 'COURSE_VERSION' && !e.newVersionHasDocument) { return 'Upload the syllabus for the new course version first.'; }
  return null;
}
