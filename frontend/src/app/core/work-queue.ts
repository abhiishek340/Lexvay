import { Params } from '@angular/router';
import type { IconName } from '../theme/icons';
import { WorkItem, WorkQueueKind } from './models';

/** One row of the work queue popover: an icon, a count-first title, where it is, and the screen it opens. */
export interface WorkItemView {
  readonly icon: IconName;
  readonly title: string;
  readonly detail: string;
  readonly commands: readonly unknown[];
  readonly queryParams?: Params;
}

/** The popover lists at most eight items; the badge counts everything (GET /work-queue/count). */
export const WORK_QUEUE_LIMIT = 8;

const ICON: Record<WorkQueueKind, IconName> = {
  REQUIREMENTS_TO_REVIEW: 'phosphorListChecks',
  EVIDENCE_REQUESTS_OPEN: 'phosphorTray',
  RULE_VERSIONS_TO_APPROVE: 'phosphorScales',
  EXTRACTION_FAILED: 'phosphorWarningCircle',
  CHANGES_OPEN: 'phosphorGitDiff',
  READY_TO_SIGN: 'phosphorSignature',
  ASSESSMENTS_FAILED: 'phosphorWarning',
};

const counted = (n: number, one: string, many: string): string => `${n} ${n === 1 ? one : many}`;

function title(item: WorkItem): string {
  const n = item.count;
  switch (item.kind) {
    case 'REQUIREMENTS_TO_REVIEW': return `${n} to review`;
    case 'EVIDENCE_REQUESTS_OPEN': return counted(n, 'evidence request', 'evidence requests');
    case 'RULE_VERSIONS_TO_APPROVE': return `${counted(n, 'rule version', 'rule versions')} to approve`;
    case 'EXTRACTION_FAILED': return counted(n, 'extraction failed', 'extractions failed');
    case 'CHANGES_OPEN': return counted(n, 'open change', 'open changes');
    case 'READY_TO_SIGN': return 'Ready to sign';
    case 'ASSESSMENTS_FAILED': return counted(n, 'failed assessment', 'failed assessments');
  }
}

/**
 * How a work item reads and where it leads. Program-scoped items open their program's screen; rule versions and
 * changes belong to a jurisdiction or the tenant, so they open in the program the person is working on (or the
 * first one the change touches), and on the program list when there is none.
 */
export function workItemView(item: WorkItem, currentProgramId: string | null, jurisdictionName: (code: string) => string): WorkItemView {
  const place = item.jurisdictionCode ? jurisdictionName(item.jurisdictionCode) : null;
  const detail = [place, item.programName].filter((part): part is string => !!part).join(' · ');
  const program = item.programId ?? item.ids.programIds?.[0] ?? currentProgramId;
  const view = { icon: ICON[item.kind], title: title(item) };
  const under = (...screen: string[]): readonly unknown[] => (program ? ['/programs', program, ...screen] : ['/']);
  switch (item.kind) {
    case 'EVIDENCE_REQUESTS_OPEN':
      return { ...view, detail, commands: under('requests') };
    case 'REQUIREMENTS_TO_REVIEW':
    case 'READY_TO_SIGN':
      return { ...view, detail, commands: under('review'), queryParams: item.jurisdictionCode ? { jurisdiction: item.jurisdictionCode } : undefined };
    case 'RULE_VERSIONS_TO_APPROVE':
    case 'EXTRACTION_FAILED':
      return { ...view, detail, commands: under('rules', ...(item.ids.ruleVersionIds?.slice(0, 1) ?? [])) };
    case 'CHANGES_OPEN':
      return { ...view, detail: item.ids.changeKind === 'COURSE_VERSION' ? 'New course versions' : 'New rule versions', commands: under('changes') };
    case 'ASSESSMENTS_FAILED':
      return { ...view, detail, commands: under('overview') };
  }
}
