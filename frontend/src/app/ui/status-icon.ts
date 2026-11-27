import { Component, computed, input } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { phosphorArrowsClockwise, phosphorCheckCircle, phosphorQuestion, phosphorXCircle } from '@ng-icons/phosphor-icons/regular';
import { phosphorCheckCircleFill, phosphorPaperPlaneTiltFill, phosphorXCircleFill } from '@ng-icons/phosphor-icons/fill';
import { DecisionKind, Finding } from '../core/models';
import { requirementStatus } from './status';

/** Every icon requirementStatus can name; the component registers them itself. */
export const STATUS_ICONS = {
  phosphorArrowsClockwise, phosphorCheckCircle, phosphorQuestion, phosphorXCircle, phosphorCheckCircleFill, phosphorPaperPlaneTiltFill, phosphorXCircleFill,
};

/**
 * A requirement's one status icon (finding and decision together, see requirementStatus):
 *   <lx-status-icon [finding]="m.finding" [decision]="m.decision?.kind ?? null" [stale]="m.stale" />
 * The icon carries the words as its accessible name; `showLabel` also prints them beside it.
 */
@Component({
  selector: 'lx-status-icon',
  imports: [NgIcon],
  template: `
    <ng-icon [name]="status().icon" [size]="size()" aria-hidden="true" />
    @if (showLabel()) { <span class="lx-status-text">{{ status().label }}</span> } @else { <span class="lx-sr-only">{{ status().label }}</span> }
  `,
  host: { '[class]': '"lx-tone-" + status().tone' },
  viewProviders: [provideIcons(STATUS_ICONS)],
  styles: `
    :host { display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
    ng-icon { flex: none; }
    :host(.lx-tone-found) ng-icon { color: var(--p-lx-found); }
    :host(.lx-tone-ambiguous) ng-icon { color: var(--p-lx-ambiguous); }
    :host(.lx-tone-missing) ng-icon { color: var(--p-lx-missing); }
    :host(.lx-tone-muted) ng-icon { color: var(--p-lx-text-2); }
    .lx-status-text { color: var(--p-lx-text-2); }
  `,
})
export class StatusIcon {
  readonly finding = input.required<Finding>();
  readonly decision = input<DecisionKind | null>(null);
  readonly stale = input(false);
  readonly showLabel = input(false);
  readonly size = input('18');
  readonly status = computed(() => requirementStatus(this.finding(), this.decision(), this.stale()));
}
