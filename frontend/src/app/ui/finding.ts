import { Component, computed, input } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { DECISION_ICON, DECISION_LABEL, FINDING_ICON, FINDING_LABEL } from '../core/labels';
import { Decision, Finding } from '../core/models';

const FINDING_CLASS: Record<Finding, string> = { EVIDENCE_FOUND: 'lx-found', AMBIGUOUS: 'lx-ambiguous', EVIDENCE_MISSING: 'lx-missing' };

/** A proposed finding: shape, word and colour together, never colour alone. */
@Component({
  selector: 'lx-finding',
  imports: [NgIcon],
  template: `<ng-icon [name]="icon()" [size]="large() ? '18' : '14'" aria-hidden="true" /><span>{{ label() }}</span>`,
  host: { '[class]': 'cssClass()', '[class.lx-finding-large]': 'large()' },
  styles: `
    :host { display: inline-flex; align-items: center; gap: 6px; font-weight: 500; white-space: nowrap; }
    :host(.lx-finding-large) { font-size: 15px; gap: 8px; }
    ng-icon { flex: none; }
  `,
})
export class FindingLabel {
  readonly finding = input.required<Finding>();
  readonly large = input(false);
  readonly icon = computed(() => FINDING_ICON[this.finding()]);
  readonly label = computed(() => FINDING_LABEL[this.finding()]);
  readonly cssClass = computed(() => FINDING_CLASS[this.finding()]);
}

/** Where a requirement stands in review: decided (with what), outdated by a change, or still open. */
@Component({
  selector: 'lx-decision-state',
  imports: [NgIcon],
  template: `<ng-icon [name]="icon()" size="14" aria-hidden="true" /><span>{{ label() }}</span>`,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 6px; color: var(--p-lx-text-2); white-space: nowrap; }
    :host(.lx-decided) { color: var(--p-lx-text); }
    ng-icon { flex: none; }
  `,
  host: { '[class.lx-decided]': 'decision() !== null && !stale()' },
})
export class DecisionState {
  readonly decision = input<Decision | null>(null);
  readonly stale = input(false);
  readonly icon = computed(() => {
    const d = this.decision();
    if (this.stale()) { return 'phosphorArrowsClockwise' as const; }
    return d ? DECISION_ICON[d.kind] : ('phosphorCircleDashed' as const);
  });
  readonly label = computed(() => {
    const d = this.decision();
    if (this.stale()) { return d ? `Outdated: ${DECISION_LABEL[d.kind].toLowerCase()}` : 'Outdated'; }
    return d ? DECISION_LABEL[d.kind] : 'Needs review';
  });
}
