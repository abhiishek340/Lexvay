import type { IconName } from '../theme/icons';
import { RuleVersionView } from './models';

export type RuleVersionStatus = 'EXTRACTING' | 'EXTRACTION_FAILED' | 'NEEDS_APPROVAL' | 'APPROVED' | 'SUPERSEDED';

/** Each status has its own word and shape; the Tag colour only repeats what they say. */
export const RULE_STATUS: Record<RuleVersionStatus, { label: string; icon: IconName; severity: 'info' | 'danger' | 'warn' | 'success' | 'secondary' }> = {
  EXTRACTING: { label: 'Extracting', icon: 'phosphorSpinner', severity: 'info' },
  EXTRACTION_FAILED: { label: 'Extraction failed', icon: 'phosphorWarningCircle', severity: 'danger' },
  NEEDS_APPROVAL: { label: 'Needs approval', icon: 'phosphorCircleDashed', severity: 'warn' },
  APPROVED: { label: 'Approved', icon: 'phosphorSealCheck', severity: 'success' },
  SUPERSEDED: { label: 'Superseded', icon: 'phosphorArchive', severity: 'secondary' },
};

/**
 * Where a rule version stands. A version another version supersedes is Superseded whatever else is true of it.
 * Otherwise an approved list is Approved and an unapproved one with requirements Needs approval, whatever
 * happened to the extraction job (a reviewer may have entered the list after it failed). Without requirements
 * the extraction job decides: still queued or running is Extracting, failed is Extraction failed.
 */
export function ruleVersionStatus(view: RuleVersionView, all: readonly RuleVersionView[]): RuleVersionStatus {
  if (all.some((other) => other.id !== view.id && other.supersedesId === view.id)) { return 'SUPERSEDED'; }
  if (view.approval) { return 'APPROVED'; }
  if (view.requirementCount > 0) { return 'NEEDS_APPROVAL'; }
  const job = view.extraction?.status;
  if (job === 'QUEUED' || job === 'RUNNING') { return 'EXTRACTING'; }
  if (job === 'FAILED') { return 'EXTRACTION_FAILED'; }
  return 'NEEDS_APPROVAL';
}

/** The Rules badge: unapproved versions that have requirements to approve. */
export function versionsWaitingForApproval(all: readonly RuleVersionView[]): number {
  return all.filter((v) => v.requirementCount > 0 && ruleVersionStatus(v, all) === 'NEEDS_APPROVAL').length;
}

/** The list polls only while some extraction is still running. */
export function anyExtracting(all: readonly RuleVersionView[]): boolean {
  return all.some((v) => ruleVersionStatus(v, all) === 'EXTRACTING');
}

export interface RuleVersionRow { view: RuleVersionView; status: RuleVersionStatus }
export interface RuleVersionGroup { code: string; name: string; target: boolean; rows: RuleVersionRow[] }

/**
 * Versions grouped by jurisdiction: the program's target jurisdictions first, in the program's order, then the
 * others by code. Within a group the newest effective date comes first, then the newest entry.
 */
export function groupRuleVersions(all: readonly RuleVersionView[], targets: readonly string[], name: (code: string) => string): RuleVersionGroup[] {
  const byCode = new Map<string, RuleVersionRow[]>();
  for (const view of all) {
    const rows = byCode.get(view.jurisdiction) ?? [];
    rows.push({ view, status: ruleVersionStatus(view, all) });
    byCode.set(view.jurisdiction, rows);
  }
  const others = [...byCode.keys()].filter((code) => !targets.includes(code)).sort();
  return [...targets.filter((code) => byCode.has(code)), ...others].map((code) => ({
    code,
    name: name(code),
    target: targets.includes(code),
    rows: byCode.get(code)!.sort(newestFirst),
  }));
}

function newestFirst(a: RuleVersionRow, b: RuleVersionRow): number {
  return b.view.effectiveFrom.localeCompare(a.view.effectiveFrom) || b.view.createdAt.localeCompare(a.view.createdAt);
}

/**
 * Versions a version can be compared with, or a new one can replace: approved, the same jurisdiction and license
 * type, not the version itself; newest effective date first.
 */
export function comparableVersions(all: readonly RuleVersionView[], jurisdiction: string, licenseType: string, exceptId: string | null): RuleVersionView[] {
  return all
    .filter((v) => v.approval && v.jurisdiction === jurisdiction && v.licenseType === licenseType && v.id !== exceptId)
    .sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom));
}
