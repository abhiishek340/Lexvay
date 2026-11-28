import { RuleVersionView } from './models';

export type RuleVersionStatus = 'EXTRACTING' | 'EXTRACTION_FAILED' | 'NEEDS_APPROVAL' | 'APPROVED' | 'SUPERSEDED';

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

/** Newest effective date first; a version with no recorded date has none to compare and comes before the dated ones. */
function newestDateFirst(a: string | null, b: string | null): number {
  if (a === b) { return 0; }
  if (a === null) { return -1; }
  return b === null ? 1 : b.localeCompare(a);
}

function newestFirst(a: RuleVersionRow, b: RuleVersionRow): number {
  return newestDateFirst(a.view.effectiveFrom, b.view.effectiveFrom) || b.view.createdAt.localeCompare(a.view.createdAt);
}

/** "2026-07-01", or the words for a version whose date nobody has recorded. */
export function effectiveText(view: Pick<RuleVersionView, 'effectiveFrom'>): string {
  return view.effectiveFrom ?? 'date not recorded';
}

/**
 * Versions a version can be compared with, or a new one can replace: approved, the same jurisdiction and license
 * type, not the version itself; newest effective date first.
 */
export function comparableVersions(all: readonly RuleVersionView[], jurisdiction: string, licenseType: string, exceptId: string | null): RuleVersionView[] {
  return all
    .filter((v) => v.approval && v.jurisdiction === jurisdiction && v.licenseType === licenseType && v.id !== exceptId)
    .sort((a, b) => newestDateFirst(a.effectiveFrom, b.effectiveFrom));
}

/**
 * The version Rules opens on when the address names none, so the screen never starts on an empty detail: the first, in
 * list order, that waits for a person (needs approval or its extraction failed), otherwise the newest approved version
 * of the first state that has one. Null only when there is no version at all.
 */
export function openingVersion(groups: readonly RuleVersionGroup[]): string | null {
  const rows = groups.flatMap((g) => g.rows);
  const waiting = rows.find((r) => r.status === 'NEEDS_APPROVAL' || r.status === 'EXTRACTION_FAILED');
  return (waiting ?? rows.find((r) => r.status === 'APPROVED') ?? rows[0])?.view.id ?? null;
}
