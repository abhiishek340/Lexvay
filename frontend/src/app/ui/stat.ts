import { Component, input } from '@angular/core';
import { CountUp } from './count-up';

/**
 * A headline number on the surface of a hero panel (DESIGN.md "Stat"): the number counts up on first view, the
 * label has at most three words, and a ring (or any small figure) can be projected before it.
 *   <lx-stat [value]="33" label="To review"><lx-progress-ring … /></lx-stat>
 *   <lx-stat [value]="1" [of]="5" label="Signed" />        <lx-stat [value]="72" suffix="%" label="Evidence found" />
 * No border and no card of its own.
 */
@Component({
  selector: 'lx-stat',
  imports: [CountUp],
  template: `
    <ng-content />
    <div class="lx-stat-text">
      <p class="lx-stat-value">
        <span class="lx-number" [lxCountUp]="value()" [suffix]="suffix()"></span>
        @if (of() !== null) { <span class="lx-stat-of">of {{ of() }}</span> }
      </p>
      <p class="lx-stat-label">{{ label() }}</p>
    </div>
  `,
  styles: `
    :host { display: flex; align-items: center; gap: 16px; min-width: 0; }
    .lx-stat-text { min-width: 0; }
    .lx-stat-value { display: flex; align-items: baseline; gap: 6px; white-space: nowrap; }
    .lx-stat-of { font-size: 15px; font-weight: 500; color: var(--p-lx-text-2); }
    .lx-stat-label { margin-top: 2px; font-size: 13px; line-height: 20px; color: var(--p-lx-text-2); }
  `,
})
export class Stat {
  readonly value = input.required<number>();
  readonly label = input.required<string>();
  readonly suffix = input('');
  /** "1 of 5": the whole the value is part of. */
  readonly of = input<number | null>(null);
}
