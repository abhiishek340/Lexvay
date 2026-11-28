import { Component, computed, input } from '@angular/core';
import { DECISION_LABEL, FINDING_LABEL } from '../core/labels';
import { DecisionKind, Finding } from '../core/models';
import { MarkShape, MarkShapeIcon } from './mark-shape';

/**
 * Every status a mark stands for (DESIGN.md "Status marks"): the three findings, the three decisions, and the states
 * around them. A Finding or a DecisionKind is a Status as it is.
 */
export type Status = Finding | DecisionKind | 'NOT_REVIEWED' | 'OUTDATED' | 'BLOCKED';

/** found and missing take their colour; neutral is ink-2; quiet is an ink-3 mark beside an ink-2 word. */
export type MarkTone = 'found' | 'missing' | 'neutral' | 'quiet';

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
 * whose neighbouring text already says it. The mark itself is always hidden from assistive technology.
 */
@Component({
  selector: 'lx-status-mark',
  imports: [MarkShapeIcon],
  template: `
    <lx-mark-shape class="lx-mark" [shape]="spec().shape" />
    <span class="lx-word" [class.lx-sr-only]="quiet()">{{ label() ?? spec().word }}</span>
  `,
  host: { '[attr.data-status]': 'status()', '[attr.data-tone]': 'spec().tone' },
  styles: `
    :host { display: inline-flex; align-items: center; gap: 8px; white-space: nowrap; }
    :host([data-tone='found']) .lx-mark { color: var(--p-lx-found); }
    :host([data-tone='missing']) .lx-mark { color: var(--p-lx-missing); }
    :host([data-tone='neutral']) .lx-mark { color: var(--p-lx-ink-2); }
    :host([data-tone='quiet']) .lx-mark { color: var(--p-lx-ink-3); }
    :host([data-tone='quiet']) .lx-word { color: var(--p-lx-ink-2); }
  `,
})
export class StatusMark {
  readonly status = input.required<Status>();
  readonly label = input<string | null>(null);
  readonly quiet = input(false);
  readonly spec = computed(() => STATUS_MARK[this.status()]);
}
