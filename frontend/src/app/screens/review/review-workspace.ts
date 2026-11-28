import { computed, signal } from '@angular/core';
import { numericCheck } from '../../core/labels';
import { DecisionKind, Evidence, EvidenceResponse, Finding, JurisdictionSummary, Mapping } from '../../core/models';
import type { IconName } from '../../theme/icons';
import type { MeterSegment } from '../../ui/meter';
import type { StateProgress } from '../../ui/state-tabs';

export type ReviewFilter = 'ALL' | 'NEEDS_REVIEW' | Finding;

/**
 * The list filter as a segmented icon control (DESIGN.md "Review"). Each segment's words carry the count first
 * ("7 to review", word budget): they are its accessible name and its tooltip, so the icons need no legend.
 */
export const REVIEW_FILTERS: { value: ReviewFilter; icon: IconName; noun: string }[] = [
  { value: 'ALL', icon: 'phosphorListBullets', noun: 'requirements' },
  { value: 'NEEDS_REVIEW', icon: 'phosphorCircleDashed', noun: 'to review' },
  { value: 'EVIDENCE_FOUND', icon: 'phosphorCheckCircle', noun: 'found' },
  { value: 'AMBIGUOUS', icon: 'phosphorQuestion', noun: 'ambiguous' },
  { value: 'EVIDENCE_MISSING', icon: 'phosphorXCircle', noun: 'missing' },
];

export function filterLabel(filter: ReviewFilter, count: number): string {
  const noun = REVIEW_FILTERS.find((f) => f.value === filter)!.noun;
  return `${count} ${count === 1 && filter === 'ALL' ? 'requirement' : noun}`;
}

/**
 * How a finding was reached, in one line beside the finding chip, with the explanation for its InfoTip. The line
 * states facts the record holds (the numeric check, the cited page); the model's own rationale is shown apart.
 */
export interface CheckMethod { icon: IconName; line: string; detail: string }

export function checkMethod(m: Mapping): CheckMethod {
  if (m.method === 'RULES_ENGINE') {
    const check = numericCheck(m.requirement);
    return {
      icon: 'phosphorCalculator', line: check ? `Rules engine: ${check}` : 'Rules engine calculation',
      detail: 'The number recorded for the program was compared with the requirement by code, not by the model. There is no passage to quote.',
    };
  }
  if (!m.evidence) {
    return {
      icon: 'phosphorMagnifyingGlass', line: 'No passage found',
      detail: 'Candidate passages from the program documents were searched and shown to the model, which proposed no passage that answers this requirement.',
    };
  }
  return {
    icon: 'phosphorMagnifyingGlass', line: `Quote verified on page ${m.evidence.pageNumber}`,
    detail: 'The model proposed this passage. The quote was then verified character for character against the stored page text before it was shown here.',
  };
}

/** Decided of total requirements per state of the latest assessment: the counts on the state tabs. */
export function stateProgress(summaries: readonly JurisdictionSummary[]): ReadonlyMap<string, StateProgress> {
  return new Map(summaries.map((s) => [s.jurisdiction, { value: s.decided, total: s.total, label: `${s.decided} of ${s.total} reviewed` }]));
}

/** Which way the requirement card enters: from the right when moving down the list, from the left when moving up. */
export type Travel = 'forward' | 'back';

/** A decision counts only while it is current: a change that makes the mapping stale reopens it. */
export function isDecided(m: Mapping): boolean {
  return m.decision !== null && !m.stale;
}

export function matchesFilter(m: Mapping, filter: ReviewFilter): boolean {
  if (filter === 'ALL') { return true; }
  if (filter === 'NEEDS_REVIEW') { return !isDecided(m); }
  return m.finding === filter;
}

export type ShortcutCommand = 'next' | 'previous' | 'accept' | 'request' | 'gap' | 'help';

export interface KeyLike {
  key: string;
  ctrlKey?: boolean;
  metaKey?: boolean;
  altKey?: boolean;
  /** The focused element's tag name and whether it is editable decide if the key is text input. */
  targetTag?: string;
  targetEditable?: boolean;
}

const SHORTCUTS: Record<string, ShortcutCommand> = { j: 'next', k: 'previous', a: 'accept', r: 'request', g: 'gap', '?': 'help' };

export const SHORTCUT_HELP: { keys: string; command: ShortcutCommand; description: string }[] = [
  { keys: 'J', command: 'next', description: 'Next requirement' },
  { keys: 'K', command: 'previous', description: 'Previous requirement' },
  { keys: 'A', command: 'accept', description: 'Accept evidence' },
  { keys: 'R', command: 'request', description: 'Request evidence from the program' },
  { keys: 'G', command: 'gap', description: 'Open the note for a gap, then Confirm gap' },
  { keys: '?', command: 'help', description: 'Show this list' },
];

/** Single-key shortcuts, never while the person is typing or holding a modifier. */
export function shortcutFor(event: KeyLike): ShortcutCommand | null {
  if (event.ctrlKey || event.metaKey || event.altKey) { return null; }
  const tag = (event.targetTag ?? '').toUpperCase();
  if (event.targetEditable || tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') { return null; }
  return SHORTCUTS[event.key.length === 1 ? event.key.toLowerCase() : ''] ?? null;
}

/** Why an action cannot be recorded yet, or null when it can. */
export function decisionBlocker(kind: DecisionKind, note: string): string | null {
  return kind === 'GAP_CONFIRMED' && note.trim() === '' ? 'Confirming a gap requires a note that says why.' : null;
}

/**
 * What the Evidence pane shows for one requirement: the system's proposed evidence first and unchanged, then the
 * passages people provided in answer to evidence requests, newest first (F4). `answered`: a person answered and no
 * decision has been made since, so the decision note asks which evidence the reviewer relied on.
 */
export interface EvidenceView {
  system: Evidence | null;
  provided: readonly EvidenceResponse[];
  /** Responses exist but are not fetched yet (or more arrived since). */
  providedLoading: boolean;
  answered: boolean;
}

export function evidenceView(m: Mapping, responses: ReadonlyMap<string, readonly EvidenceResponse[]>): EvidenceView {
  const cached = responses.get(m.id);
  const fresh = cached !== undefined && cached.length === m.responseCount;
  return {
    system: m.evidence,
    provided: cached ?? [],
    providedLoading: m.responseCount > 0 && !fresh,
    answered: m.responseCount > 0 && m.decision === null,
  };
}

/**
 * The review workspace for one jurisdiction: the requirement list, its filter, the selection and the
 * progress. Plain signals with no Angular dependencies so every rule here is unit-tested.
 */
export class ReviewWorkspace {
  readonly mappings = signal<Mapping[]>([]);
  readonly filter = signal<ReviewFilter>('ALL');
  readonly selectedId = signal<string | null>(null);
  /** The direction of the last change of selection, for the card's entrance. */
  readonly travel = signal<Travel>('forward');
  /** Evidence responses per mapping id, fetched with the selected mapping and kept for the screen's lifetime. */
  readonly responses = signal<ReadonlyMap<string, readonly EvidenceResponse[]>>(new Map());

  readonly visible = computed(() => this.mappings().filter((m) => matchesFilter(m, this.filter())));
  readonly selected = computed(() => this.mappings().find((m) => m.id === this.selectedId()) ?? null);
  readonly evidence = computed(() => {
    const m = this.selected();
    return m ? evidenceView(m, this.responses()) : null;
  });
  readonly decided = computed(() => this.mappings().filter(isDecided).length);
  readonly total = computed(() => this.mappings().length);
  readonly allDecided = computed(() => this.total() > 0 && this.decided() === this.total());
  readonly progressLabel = computed(() => `${this.decided()} of ${this.total()} reviewed`);
  /** The header's Meter: the current decisions by kind, in the order DESIGN.md fixes, the undecided rest left empty. */
  readonly meter = computed<MeterSegment[]>(() => {
    const decided = (kind: DecisionKind): number => this.mappings().filter((m) => isDecided(m) && m.decision!.kind === kind).length;
    return [{ tone: 'found', value: decided('ACCEPTED') }, { tone: 'ambiguous', value: decided('EVIDENCE_REQUESTED') }, { tone: 'missing', value: decided('GAP_CONFIRMED') }];
  });
  readonly counts = computed(() => {
    const counts = {} as Record<ReviewFilter, number>;
    for (const f of REVIEW_FILTERS) {
      counts[f.value] = this.mappings().filter((m) => matchesFilter(m, f.value)).length;
    }
    return counts;
  });
  /** The filter segments with their current words. */
  readonly filterOptions = computed(() => REVIEW_FILTERS.map((f) => ({ value: f.value, icon: f.icon, label: filterLabel(f.value, this.counts()[f.value]) })));

  /** A new list (a jurisdiction was opened): start at the first requirement that still needs a decision. */
  load(mappings: Mapping[]): void {
    this.mappings.set(mappings);
    this.filter.set('ALL');
    this.selectedId.set((mappings.find((m) => !isDecided(m)) ?? mappings[0])?.id ?? null);
  }

  setResponses(mappingId: string, responses: readonly EvidenceResponse[]): void {
    this.responses.update((all) => new Map(all).set(mappingId, responses));
  }

  select(id: string): void {
    if (this.mappings().some((m) => m.id === id)) {
      this.goTo(id);
    }
  }

  /** Moves the selection, noting the direction by list order (a wrap to the top after a decision still reads forward). */
  private goTo(id: string, travel?: Travel): void {
    const all = this.mappings();
    const from = all.findIndex((m) => m.id === this.selectedId());
    this.travel.set(travel ?? (all.findIndex((m) => m.id === id) < from ? 'back' : 'forward'));
    this.selectedId.set(id);
  }

  /** J and K: move within the visible list; stays put at either end. */
  move(delta: 1 | -1): Mapping | null {
    const list = this.visible();
    if (list.length === 0) { return null; }
    const index = list.findIndex((m) => m.id === this.selectedId());
    const next = index === -1 ? list[0] : list[Math.min(list.length - 1, Math.max(0, index + delta))];
    this.goTo(next.id, delta === 1 ? 'forward' : 'back');
    return next;
  }

  /** Keeps the selection when it is still visible under the new filter, otherwise takes the first visible row. */
  setFilter(filter: ReviewFilter): void {
    this.filter.set(filter);
    const list = this.visible();
    if (!list.some((m) => m.id === this.selectedId())) {
      this.selectedId.set(list[0]?.id ?? null);
    }
  }

  /**
   * Records the server's updated mapping and advances to the next visible requirement that still needs
   * a decision, wrapping around; when there is none it stays on the decided one. Returns the new selection.
   */
  applyDecision(updated: Mapping): Mapping | null {
    this.mappings.update((list) => list.map((m) => (m.id === updated.id ? updated : m)));
    const all = this.mappings();
    const start = all.findIndex((m) => m.id === updated.id) + 1;
    const filter = this.filter();
    const next = [...all.slice(start), ...all.slice(0, start)].find((m) => !isDecided(m) && matchesFilter(m, filter));
    const list = this.visible();
    if (next) {
      this.goTo(next.id, 'forward');
    } else if (!list.some((m) => m.id === this.selectedId())) {
      this.selectedId.set(list[0]?.id ?? null);
    }
    return this.selected();
  }
}
