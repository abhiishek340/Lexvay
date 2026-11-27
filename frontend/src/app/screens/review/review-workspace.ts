import { computed, signal } from '@angular/core';
import { DecisionKind, Finding, Mapping } from '../../core/models';

export type ReviewFilter = 'ALL' | 'NEEDS_REVIEW' | Finding;

export const REVIEW_FILTERS: { value: ReviewFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'NEEDS_REVIEW', label: 'Needs review' },
  { value: 'EVIDENCE_FOUND', label: 'Found' },
  { value: 'AMBIGUOUS', label: 'Ambiguous' },
  { value: 'EVIDENCE_MISSING', label: 'Missing' },
];

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
  { keys: 'R', command: 'request', description: 'Request evidence from program director' },
  { keys: 'G', command: 'gap', description: 'Write the note for a gap (then choose Confirm gap)' },
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
 * The review workspace for one jurisdiction: the requirement list, its filter, the selection and the
 * progress. Plain signals with no Angular dependencies so every rule here is unit-tested.
 */
export class ReviewWorkspace {
  readonly mappings = signal<Mapping[]>([]);
  readonly filter = signal<ReviewFilter>('ALL');
  readonly selectedId = signal<string | null>(null);

  readonly visible = computed(() => this.mappings().filter((m) => matchesFilter(m, this.filter())));
  readonly selected = computed(() => this.mappings().find((m) => m.id === this.selectedId()) ?? null);
  readonly decided = computed(() => this.mappings().filter(isDecided).length);
  readonly total = computed(() => this.mappings().length);
  readonly allDecided = computed(() => this.total() > 0 && this.decided() === this.total());
  readonly progressLabel = computed(() => `${this.decided()} of ${this.total()} reviewed`);
  readonly counts = computed(() => {
    const counts = {} as Record<ReviewFilter, number>;
    for (const f of REVIEW_FILTERS) {
      counts[f.value] = this.mappings().filter((m) => matchesFilter(m, f.value)).length;
    }
    return counts;
  });

  /** A new list (a jurisdiction was opened): start at the first requirement that still needs a decision. */
  load(mappings: Mapping[]): void {
    this.mappings.set(mappings);
    this.filter.set('ALL');
    this.selectedId.set((mappings.find((m) => !isDecided(m)) ?? mappings[0])?.id ?? null);
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
      this.selectedId.set(next.id);
    } else if (!list.some((m) => m.id === this.selectedId())) {
      this.selectedId.set(list[0]?.id ?? null);
    }
    return this.selected();
  }
}
