import { formatDate } from '@angular/common';
import { Params } from '@angular/router';
import { AssessmentView, Determination, JurisdictionSummary, Outcome, Program, RuleVersionView, WorkItem, WorkQueueKind } from '../../core/models';
import { JurisdictionState, jurisdictionRows, overviewCounts } from '../../core/overview';
import { comparableVersions } from '../../core/rules';
import { WorkItemView, workItemView } from '../../core/work-queue';
import { MeterSegment } from '../../ui/meter';

/** A link to another screen: where it goes and the words on it. */
export interface ScreenLink { readonly label: string; readonly commands: readonly unknown[]; readonly queryParams?: Params }

/** A state's current signature, as its row shows it. */
export interface Signature { readonly outcome: Outcome; readonly signer: string; readonly signedAt: string }

/**
 * One target state in the overview's States table (DESIGN.md "Program overview"). `action` is the one thing to do next,
 * which changes with the stage: Review while requirements wait, Sign once all are decided (for a signer), Report once
 * signed; none before the first assessment.
 */
export interface StateRow {
  readonly code: string;
  readonly name: string;
  readonly stage: JurisdictionState;
  /** The rule version: the one signed, else the newest approved for the program's license; null when there is none. */
  readonly rules: string | null;
  readonly decided: number;
  readonly total: number;
  readonly meter: readonly MeterSegment[];
  /** "6 of 8 decided", or "Not assessed". */
  readonly progress: string;
  /** Rounded share of requirements whose proposed finding is evidence found; null before an assessment. */
  readonly foundPercent: number | null;
  readonly signature: Signature | null;
  /** "Signed by Dana Whitfield, 27 Nov", "Reopened, not signed" or "Not signed". */
  readonly determination: string;
  readonly action: ScreenLink | null;
}

const percent = (part: number, whole: number): number => (whole > 0 ? Math.round((part / whole) * 100) : 0);

function rulesLabel(program: Program, code: string, signed: Determination | null, versions: readonly RuleVersionView[]): string | null {
  return signed?.ruleVersionLabel ?? comparableVersions(versions, code, program.licenseType, null)[0]?.label ?? null;
}

function progressOf(s: JurisdictionSummary | null): Pick<StateRow, 'decided' | 'total' | 'meter' | 'progress' | 'foundPercent'> {
  if (!s) {
    return { decided: 0, total: 0, meter: [], progress: 'Not assessed', foundPercent: null };
  }
  return {
    decided: s.decided, total: s.total, meter: [{ tone: 'decided', value: s.decided }],
    progress: `${s.decided} of ${s.total} decided`, foundPercent: percent(s.evidenceFound, s.total),
  };
}

/** The program's target states in its own order, as rows of the States table. */
export function stateRows(program: Program, assessment: AssessmentView | null, determinations: Determination[], versions: readonly RuleVersionView[],
  signer: boolean, name: (code: string) => string): StateRow[] {
  return jurisdictionRows(program, assessment, determinations).map((row) => {
    const at = { jurisdiction: row.code };
    const review: ScreenLink = { label: 'Review', commands: ['/programs', program.id, 'review'], queryParams: at };
    const signed = row.signed;
    const action: ScreenLink | null = row.state === 'SIGNED' ? { label: 'Report', commands: ['/programs', program.id, 'report'], queryParams: at }
      : row.state === 'READY' ? (signer ? { ...review, label: 'Sign' } : review)
      : row.state === 'IN_REVIEW' ? review : null;
    return {
      code: row.code, name: name(row.code), stage: row.state, rules: rulesLabel(program, row.code, signed, versions), ...progressOf(row.summary),
      signature: signed ? { outcome: signed.outcome, signer: signed.signedByName, signedAt: signed.signedAt } : null,
      determination: signed ? `Signed by ${signed.signedByName}, ${formatDate(signed.signedAt, 'd MMM', 'en-US')}`
        : row.reopened ? 'Reopened, not signed' : 'Not signed',
      action,
    };
  });
}

/** The three figures under the title: what waits for review, the share of evidence found, and the signatures. */
export interface OverviewFigures { readonly toReview: number; readonly foundPercent: number; readonly signed: number; readonly targets: number }

export function overviewFigures(program: Program, assessment: AssessmentView | null, determinations: Determination[]): OverviewFigures {
  const counts = overviewCounts(program, assessment, determinations);
  const found = (assessment?.jurisdictions ?? []).reduce((n, s) => n + s.evidenceFound, 0);
  return { toReview: counts.waitingForReview, foundPercent: percent(found, counts.requirements), signed: counts.determinationsSigned, targets: counts.jurisdictions };
}

/**
 * The title row's one primary action, when there is one: Run assessment before the first finished assessment, and
 * Sign once every requirement of every unsigned state is decided (for a signer; it opens the first such state).
 */
export type PrimaryAction = { readonly kind: 'run' } | ({ readonly kind: 'sign' } & ScreenLink);

export function primaryAction(assessment: AssessmentView | null, rows: readonly StateRow[], signer: boolean): PrimaryAction | null {
  if (!assessment) { return { kind: 'run' }; }
  const ready = rows.find((r) => r.stage === 'READY');
  const waiting = rows.some((r) => r.stage === 'IN_REVIEW' || r.stage === 'NOT_ASSESSED');
  return signer && ready && !waiting && ready.action ? { kind: 'sign', ...ready.action, label: `Sign ${ready.name}` } : null;
}

/** "Needs you" shows at most five items. */
export const NEEDS_YOU_LIMIT = 5;

/**
 * What finishes work soonest comes first: a state one signature from done, a failed run, open changes (they make
 * decisions stale), answers to give, then reviews, rule versions and failed extractions. Within a kind the queue's
 * own order (oldest first) holds.
 */
const PRIORITY: readonly WorkQueueKind[] = [
  'READY_TO_SIGN', 'ASSESSMENTS_FAILED', 'CHANGES_OPEN', 'EVIDENCE_REQUESTS_OPEN', 'REQUIREMENTS_TO_REVIEW', 'RULE_VERSIONS_TO_APPROVE', 'EXTRACTION_FAILED',
];

const ACTION_LABEL: Record<WorkQueueKind, string> = {
  READY_TO_SIGN: 'Sign',
  ASSESSMENTS_FAILED: 'Run again',
  CHANGES_OPEN: 'View changes',
  EVIDENCE_REQUESTS_OPEN: 'View requests',
  REQUIREMENTS_TO_REVIEW: 'Review',
  RULE_VERSIONS_TO_APPROVE: 'Review rules',
  EXTRACTION_FAILED: 'Open rules',
};

/** A row of "Needs you": the work queue's count-first title, where it is, and its one action. */
export interface NeedsYouRow extends Pick<WorkItemView, 'title' | 'commands' | 'queryParams'> {
  readonly key: string;
  readonly detail: string;
  readonly action: string;
  /** A failed assessment is run again in place; every other action opens its screen. */
  readonly run: 'navigate' | 'assessment';
}

/**
 * Whether a work item concerns this program: its own items, open changes that touch it, and rule versions of the
 * states it targets (the tenant-wide kinds carry no program).
 */
export function concernsProgram(item: WorkItem, program: Program): boolean {
  if (item.programId) { return item.programId === program.id; }
  if (item.kind === 'CHANGES_OPEN') { return item.ids.programIds?.includes(program.id) ?? false; }
  return item.jurisdictionCode !== null && program.jurisdictions.includes(item.jurisdictionCode);
}

export function needsYou(items: readonly WorkItem[], program: Program, name: (code: string) => string): NeedsYouRow[] {
  return items
    .filter((item) => concernsProgram(item, program))
    .map((item, order) => ({ item, order }))
    .sort((a, b) => PRIORITY.indexOf(a.item.kind) - PRIORITY.indexOf(b.item.kind) || a.order - b.order)
    .slice(0, NEEDS_YOU_LIMIT)
    .map(({ item }) => {
      const view = workItemView(item, program.id, name);
      // On the program's own overview the program's name says nothing; the state, or the kind of change, does.
      const detail = item.jurisdictionCode ? name(item.jurisdictionCode) : item.kind === 'CHANGES_OPEN' ? view.detail : '';
      return {
        key: `${item.kind}:${item.jurisdictionCode ?? ''}:${item.ids.changeKind ?? ''}`, title: view.title, detail, commands: view.commands, queryParams: view.queryParams,
        action: ACTION_LABEL[item.kind], run: item.kind === 'ASSESSMENTS_FAILED' ? 'assessment' : 'navigate',
      };
    });
}
