import { computed, signal } from '@angular/core';
import { numericCheck } from '../../core/labels';
import { DecisionKind, Evidence, EvidenceResponse, Finding, JurisdictionSummary, Mapping } from '../../core/models';
import type { MeterSegment } from '../../ui/meter';
import type { StateProgress } from '../../ui/state-tabs';

export type ReviewFilter = 'ALL' | 'NEEDS_REVIEW' | Finding;

/**
 * The list filter as a text tab row with counts (DESIGN.md "Review"): All, To review, Found, Ambiguous, Missing. Each
 * tab shows its word and its count; its accessible name says both ("To review, 7").
 */
export const REVIEW_FILTERS: readonly { value: ReviewFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'NEEDS_REVIEW', label: 'To review' },
  { value: 'EVIDENCE_FOUND', label: 'Found' },
  { value: 'AMBIGUOUS', label: 'Ambiguous' },
  { value: 'EVIDENCE_MISSING', label: 'Missing' },
];

/** One filter tab as the list draws it. */
export interface FilterTab { readonly value: ReviewFilter; readonly label: string; readonly count: number; readonly name: string }

/**
 * How a finding was reached, as one plain line under the finding, with the explanation for that line's tooltip (a term).
 * The line states facts the record holds (the numeric check, the cited page); the model's own rationale is shown apart.
 */
export interface CheckMethod { line: string; detail: string }

export function checkMethod(m: Mapping): CheckMethod {
  if (m.method === 'RULES_ENGINE') {
    const check = numericCheck(m.requirement);
    return {
      line: check ? `Rules engine: ${check}` : 'Rules engine calculation',
      detail: 'The number recorded for the program was compared with the requirement by code, not by the model. There is no passage to quote.',
    };
  }
  if (!m.evidence) {
    return {
      line: 'No passage found',
      detail: 'Candidate passages from the program documents were searched and shown to the model, which proposed no passage that answers this requirement.',
    };
  }
  return {
    line: `Quote verified on page ${m.evidence.pageNumber}`,
    detail: 'The model proposed this passage. The quote was then verified character for character against the stored page text before it was shown here.',
  };
}

/** "3 of 8 decided": the words of every progress count on this screen. */
export function decidedLabel(decided: number, total: number): string {
  return `${decided} of ${total} decided`;
}

/** Decided of total requirements per state of the latest assessment: the counts on the state tabs. */
export function stateProgress(summaries: readonly JurisdictionSummary[]): ReadonlyMap<string, StateProgress> {
  return new Map(summaries.map((s) => [s.jurisdiction, { value: s.decided, total: s.total, label: decidedLabel(s.decided, s.total) }]));
}

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
  { keys: 'R', command: 'request', description: 'Request evidence' },
  { keys: 'G', command: 'gap', description: 'Note a gap, then Confirm gap' },
  { keys: '?', command: 'help', description: 'Open this help' },
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
  /**
   * The requirement whose decision has just landed: its row's mark fades in over 120 ms as it changes (DESIGN.md
   * "Motion"). Cleared when the list is loaded or filtered again, so no other mark replays the fade.
   */
  readonly landed = signal<string | null>(null);
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
  readonly progressLabel = computed(() => decidedLabel(this.decided(), this.total()));
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
  /** The filter tabs with their current counts. */
  readonly filterTabs = computed<FilterTab[]>(() => REVIEW_FILTERS.map((f) => {
    const count = this.counts()[f.value];
    return { ...f, count, name: `${f.label}, ${count}` };
  }));

  /** A new list (a jurisdiction was opened): start at the first requirement that still needs a decision. */
  load(mappings: Mapping[]): void {
    this.mappings.set(mappings);
    this.filter.set('ALL');
    this.landed.set(null);
    this.selectedId.set((mappings.find((m) => !isDecided(m)) ?? mappings[0])?.id ?? null);
  }

  setResponses(mappingId: string, responses: readonly EvidenceResponse[]): void {
    this.responses.update((all) => new Map(all).set(mappingId, responses));
  }

  select(id: string): void {
    if (this.mappings().some((m) => m.id === id)) {
      this.selectedId.set(id);
    }
  }

  /** J and K: move within the visible list; stays put at either end. */
  move(delta: 1 | -1): Mapping | null {
    const list = this.visible();
    if (list.length === 0) { return null; }
    const index = list.findIndex((m) => m.id === this.selectedId());
    const next = index === -1 ? list[0] : list[Math.min(list.length - 1, Math.max(0, index + delta))];
    this.selectedId.set(next.id);
    return next;
  }

  /** Keeps the selection when it is still visible under the new filter, otherwise takes the first visible row. */
  setFilter(filter: ReviewFilter): void {
    this.filter.set(filter);
    this.landed.set(null);
    const list = this.visible();
    if (!list.some((m) => m.id === this.selectedId())) {
      this.selectedId.set(list[0]?.id ?? null);
    }
  }

  /**
   * Records the server's updated mapping (its row's mark lands) and moves the selection to the next visible requirement
   * that still needs a decision, wrapping around; when there is none it stays on the decided one. Returns the new
   * selection.
   */
  applyDecision(updated: Mapping): Mapping | null {
    this.mappings.update((list) => list.map((m) => (m.id === updated.id ? updated : m)));
    this.landed.set(updated.id);
    const all = this.mappings();
    const start = all.findIndex((m) => m.id === updated.id) + 1;
    const filter = this.filter();
    const next = [...all.slice(start), ...all.slice(0, start)].find((m) => !isDecided(m) && matchesFilter(m, filter));
    const list = this.visible();
    if (next) {
      this.selectedId.set(next.id);
    } else if (!list.some((m) => m.id === this.selectedId())) {
      this.selectedId.set(list[0]?.id ?? null);
    }
    return this.selected();
  }
}
