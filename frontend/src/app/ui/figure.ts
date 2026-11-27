import { Component, input } from '@angular/core';

/**
 * A key number in Figure type over a 12 px meta label (DESIGN.md "Figure"); no ring, no icon, and the number never animates:
 *   <lx-figure [value]="33" label="To review" />    <lx-figure [value]="1" [of]="5" label="Signed" />
 * Figures sit in a row separated by 1 px vertical rules, which the row that holds them draws.
 */
@Component({
  selector: 'lx-figure',
  template: `
    <p class="lx-figure-value"><span class="lx-figure">{{ value() }}</span>@if (of() !== null) {<span class="lx-figure-of">of {{ of() }}</span>}</p>
    <p class="lx-meta">{{ label() }}</p>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
    .lx-figure-value { display: flex; align-items: baseline; gap: 6px; height: 32px; white-space: nowrap; }
    .lx-figure-of { font-size: 15px; line-height: 24px; font-weight: 500; color: var(--p-lx-ink-2); }
  `,
})
export class Figure {
  /** The number as shown: "33", "72%". */
  readonly value = input.required<string | number>();
  readonly label = input.required<string>();
  /** "1 of 5": the whole the value is part of. */
  readonly of = input<number | null>(null);
}
