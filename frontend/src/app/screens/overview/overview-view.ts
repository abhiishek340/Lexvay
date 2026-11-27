import { FINDING_LABEL, OUTCOME_LABEL } from '../../core/labels';
import { AssessmentView, Determination, JurisdictionSummary, Program, WorkItem, WorkQueueKind } from '../../core/models';
import { JurisdictionRow, JurisdictionState, jurisdictionRows, overviewCounts } from '../../core/overview';
import { WorkItemView, workItemView } from '../../core/work-queue';
import type { IconName } from '../../theme/icons';
import { RingSegment, findingSegments } from '../../ui/progress-ring';
import { MapAction, MapState, TileStatus } from '../../ui/state-map/state-map-model';

/** The colour family of a state's status chip; the icon and the words carry the state as well. */
export type CardTone = 'found' | 'missing' | 'seal' | 'accent' | 'muted';

/**
 * One target state on the overview: its card, and the same facts as the map tile. `action` is the one thing to do
 * next, which changes with the stage: Review while requirements wait, Sign once all are decided (for a signer),
 * View report once signed; none before the first assessment.
 */
export interface StateCard {
  readonly code: string;
  readonly name: string;
  readonly stage: JurisdictionState;
  readonly tile: TileStatus;
  readonly status: string;
  readonly tone: CardTone;
  readonly icon: IconName;
  readonly reviewed: number;
  readonly total: number;
  readonly segments: readonly RingSegment[];
  readonly ringLabel: string;
  readonly action: MapAction | null;
}

/** "5 evidence found, 2 ambiguous, 1 evidence missing": a findings ring's accessible name. */
export function findingsLabel(found: number, ambiguous: number, missing: number): string {
  return `${found} ${FINDING_LABEL.EVIDENCE_FOUND.toLowerCase()}, ${ambiguous} ${FINDING_LABEL.AMBIGUOUS.toLowerCase()}, `
    + `${missing} ${FINDING_LABEL.EVIDENCE_MISSING.toLowerCase()}`;
}

const segmentsOf = (s: JurisdictionSummary | null): RingSegment[] =>
  findingSegments({ found: s?.evidenceFound ?? 0, ambiguous: s?.ambiguous ?? 0, missing: s?.evidenceMissing ?? 0, notReviewed: 0 });

const OUTCOME_LOOK = {
  MEETS: { tile: 'MEETS', tone: 'found', icon: 'phosphorCheckCircle' },
  DOES_NOT_MEET: { tile: 'DOES_NOT_MEET', tone: 'missing', icon: 'phosphorXCircle' },
  NOT_DETERMINED: { tile: 'NOT_DETERMINED', tone: 'seal', icon: 'phosphorMinusCircle' },
} as const satisfies Record<Determination['outcome'], { tile: TileStatus; tone: CardTone; icon: IconName }>;

function stateCard(row: JurisdictionRow, name: string, programId: string, signer: boolean): StateCard {
  const s = row.summary;
  const reviewed = s?.decided ?? 0;
  const total = s?.total ?? 0;
  const base = { code: row.code, name, stage: row.state, reviewed, total, segments: segmentsOf(s) };
  const ringLabel = s ? findingsLabel(s.evidenceFound, s.ambiguous, s.evidenceMissing) : 'Not assessed';
  const at = { jurisdiction: row.code };
  const review: MapAction = { label: 'Review', commands: ['/programs', programId, 'review'], queryParams: at };
  switch (row.state) {
    case 'SIGNED': {
      const outcome = row.signed!.outcome;
      return { ...base, ...OUTCOME_LOOK[outcome], status: OUTCOME_LABEL[outcome], ringLabel,
        action: { label: 'View report', commands: ['/programs', programId, 'report'], queryParams: at } };
    }
    case 'READY':
      return { ...base, tile: 'IN_REVIEW', tone: 'accent', icon: 'phosphorSignature', ringLabel,
        status: row.reopened ? 'Reopened, ready to sign' : 'Ready to sign', action: signer ? { ...review, label: 'Sign' } : review };
    case 'IN_REVIEW':
      return { ...base, tile: 'IN_REVIEW', tone: row.reopened ? 'accent' : 'muted', icon: row.reopened ? 'phosphorArrowCounterClockwise' : 'phosphorCircleDashed',
        ringLabel, status: row.reopened ? `Reopened, ${total - reviewed} to review` : `${total - reviewed} to review`, action: review };
    case 'NOT_ASSESSED':
      return { ...base, tile: 'IN_REVIEW', tone: 'muted', icon: 'phosphorCircleDashed', ringLabel, status: 'Not assessed', action: null };
  }
}

/** The program's target states in its own order, as cards. */
export function stateCards(program: Program, assessment: AssessmentView | null, determinations: Determination[], signer: boolean,
  name: (code: string) => string): StateCard[] {
  return jurisdictionRows(program, assessment, determinations).map((row) => stateCard(row, name(row.code), program.id, signer));
}

/** The map shows the cards' facts: a tile per target, coloured by stage, with the card's action. */
export function mapStates(cards: readonly StateCard[]): MapState[] {
  return cards.map((c) => ({ code: c.code, tile: c.tile, reviewed: c.reviewed, total: c.total, status: c.status, action: c.action }));
}

/** The three numbers of the hero: what waits for review, the share of evidence found, and the signatures. */
export interface HeroStats {
  readonly toReview: number;
  readonly reviewed: number;
  readonly requirements: number;
  readonly foundPercent: number;
  readonly findings: readonly RingSegment[];
  readonly findingsLabel: string;
  readonly signed: number;
  readonly targets: number;
  readonly signedRing: readonly RingSegment[];
}

export function heroStats(program: Program, assessment: AssessmentView | null, determinations: Determination[]): HeroStats {
  const counts = overviewCounts(program, assessment, determinations);
  const sum = (pick: (s: JurisdictionSummary) => number): number => (assessment?.jurisdictions ?? []).reduce((n, s) => n + pick(s), 0);
  const found = sum((s) => s.evidenceFound);
  const ambiguous = sum((s) => s.ambiguous);
  const missing = sum((s) => s.evidenceMissing);
  return {
    toReview: counts.waitingForReview,
    reviewed: counts.requirements - counts.waitingForReview,
    requirements: counts.requirements,
    foundPercent: counts.requirements > 0 ? Math.round((found / counts.requirements) * 100) : 0,
    findings: findingSegments({ found, ambiguous, missing, notReviewed: 0 }),
    findingsLabel: `${findingsLabel(found, ambiguous, missing)}, of ${counts.requirements} requirements`,
    signed: counts.determinationsSigned,
    targets: counts.jurisdictions,
    signedRing: [{ tone: 'seal', value: counts.determinationsSigned }],
  };
}

/** "Needs you" shows at most three items. */
export const NEEDS_YOU_LIMIT = 3;

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

/** A row of "Needs you": the work queue's icon and count-first title, where it is, and its one action. */
export interface NeedsYouRow extends Pick<WorkItemView, 'icon' | 'title' | 'commands' | 'queryParams'> {
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
        key: `${item.kind}:${item.jurisdictionCode ?? ''}:${item.ids.changeKind ?? ''}`, icon: view.icon, title: view.title, detail, commands: view.commands, queryParams: view.queryParams,
        action: ACTION_LABEL[item.kind], run: item.kind === 'ASSESSMENTS_FAILED' ? 'assessment' : 'navigate',
      };
    });
}
