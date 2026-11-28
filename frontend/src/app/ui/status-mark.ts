import { Component, computed, input } from '@angular/core';
import { DECISION_LABEL, FINDING_LABEL } from '../core/labels';
import { DecisionKind, Finding } from '../core/models';

/**
 * Every status a mark stands for (DESIGN.md "Status marks"): the three findings, the three decisions, and the states
 * around them. A Finding or a DecisionKind is a Status as it is.
 */
export type Status = Finding | DecisionKind | 'NOT_REVIEWED' | 'OUTDATED' | 'BLOCKED';

/** The seven 10 px shapes. Found and missing differ in shape as well as colour, so state never rests on colour. */
export type MarkShape = 'disc' | 'square' | 'half' | 'ring' | 'ring-dot' | 'dashed-ring' | 'open-square';
/**
 * found and missing take their colour; neutral is ink-2; quiet is an ink-3 mark beside an ink-2 word. ink is only ever
 * an override: a state that borrows a shape but must not borrow evidence's colour (a rule version's approval).
 */
export type MarkTone = 'found' | 'missing' | 'neutral' | 'quiet' | 'ink';

export interface MarkSpec { readonly shape: MarkShape; readonly tone: MarkTone; readonly word: string }

/** The DESIGN.md mark table, status by status. "Signed" is the Seal, not a mark. */
export const STATUS_MARK: Readonly<Record<Status, MarkSpec>> = {
  EVIDENCE_FOUND: { shape: 'disc', tone: 'found', word: FINDING_LABEL.EVIDENCE_FOUND },
  ACCEPTED: { shape: 'disc', tone: 'found', word: DECISION_LABEL.ACCEPTED },
  EVIDENCE_MISSING: { shape: 'square', tone: 'missing', word: FINDING_LABEL.EVIDENCE_MISSING },
  GAP_CONFIRMED: { shape: 'square', tone: 'missing', word: DECISION_LABEL.GAP_CONFIRMED },
  AMBIGUOUS: { shape: 'half', tone: 'neutral', word: FINDING_LABEL.AMBIGUOUS },
  NOT_REVIEWED: { shape: 'ring', tone: 'quiet', word: 'Not reviewed' },
  EVIDENCE_REQUESTED: { shape: 'ring-dot', tone: 'neutral', word: DECISION_LABEL.EVIDENCE_REQUESTED },
  OUTDATED: { shape: 'dashed-ring', tone: 'neutral', word: 'Outdated' },
  BLOCKED: { shape: 'open-square', tone: 'neutral', word: 'Blocked' },
};

/**
 * A status as a mark and a word: <lx-status-mark status="EVIDENCE_FOUND" />. `label` replaces the word where a screen
 * says more ("Evidence found, not reviewed"); `quiet` keeps the word for assistive technology only, for a dense row
 * whose neighbouring text already says it; `tone` replaces the colour where the shape is borrowed for a state that is
 * not a finding or a decision. The mark itself is always hidden from assistive technology.
 */
@Component({
  selector: 'lx-status-mark',
  template: `
    <svg class="lx-mark" viewBox="0 0 10 10" width="10" height="10" aria-hidden="true" focusable="false">
      @switch (spec().shape) {
        @case ('disc') { <circle cx="5" cy="5" r="5" class="lx-fill" /> }
        @case ('square') { <rect x="0.5" y="0.5" width="9" height="9" rx="1" class="lx-fill" /> }
        @case ('half') { <circle cx="5" cy="5" r="4.25" class="lx-stroke" /><path d="M5 .75a4.25 4.25 0 0 0 0 8.5z" class="lx-fill" /> }
        @case ('ring') { <circle cx="5" cy="5" r="4.25" class="lx-stroke" /> }
        @case ('ring-dot') { <circle cx="5" cy="5" r="4.25" class="lx-stroke" /><circle cx="5" cy="5" r="1.75" class="lx-fill" /> }
        @case ('dashed-ring') { <circle cx="5" cy="5" r="4.25" class="lx-stroke" stroke-dasharray="2 1.34" /> }
        @case ('open-square') { <rect x="1" y="1" width="8" height="8" rx="1" class="lx-stroke" /> }
      }
    </svg>
    <span class="lx-word" [class.lx-sr-only]="quiet()">{{ label() ?? spec().word }}</span>
  `,
  host: { '[attr.data-status]': 'status()', '[attr.data-tone]': 'tone() ?? spec().tone' },
  styles: `
    :host { display: inline-flex; align-items: center; gap: 8px; white-space: nowrap; }
    .lx-mark { flex: none; overflow: visible; }
    .lx-fill { fill: currentColor; }
    .lx-stroke { fill: none; stroke: currentColor; stroke-width: 1.5; }
    :host([data-tone='found']) .lx-mark { color: var(--p-lx-found); }
    :host([data-tone='missing']) .lx-mark { color: var(--p-lx-missing); }
    :host([data-tone='neutral']) .lx-mark { color: var(--p-lx-ink-2); }
    :host([data-tone='quiet']) .lx-mark { color: var(--p-lx-ink-3); }
    :host([data-tone='ink']) .lx-mark { color: var(--p-lx-ink); }
    :host([data-tone='quiet']) .lx-word { color: var(--p-lx-ink-2); }
  `,
})
export class StatusMark {
  readonly status = input.required<Status>();
  readonly label = input<string | null>(null);
  readonly quiet = input(false);
  readonly tone = input<MarkTone | null>(null);
  readonly spec = computed(() => STATUS_MARK[this.status()]);
}
