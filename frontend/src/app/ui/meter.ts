import { Component, computed, input } from '@angular/core';

/**
 * A part of a Meter. found, ambiguous and missing are the decisions or findings DESIGN.md orders them by; `decided` is
 * one neutral run in ink, for a count that has no breakdown by kind (the overview's summary carries only how many are
 * decided).
 */
export type MeterTone = 'found' | 'ambiguous' | 'missing' | 'decided';
export interface MeterSegment { readonly tone: MeterTone; readonly value: number }

const ORDER: readonly MeterTone[] = ['found', 'ambiguous', 'missing', 'decided'];

/** Each segment's share of `total` in percent, in the fixed order, capped so they never overrun the bar; empty ones dropped. */
export function meterRuns(segments: readonly MeterSegment[], total: number): { tone: MeterTone; percent: number }[] {
  let left = 100;
  return [...segments]
    .sort((a, b) => ORDER.indexOf(a.tone) - ORDER.indexOf(b.tone))
    .map((s) => {
      const percent = total > 0 ? Math.min(left, (Math.max(0, s.value) / total) * 100) : 0;
      left -= percent;
      return { tone: s.tone, percent };
    })
    .filter((run) => run.percent > 0);
}

/**
 * A 4 px, square-ended bar on sunken, drawn to scale, with the words that say what it shows (DESIGN.md "Meter"):
 *   <lx-meter [segments]="[{ tone: 'decided', value: 6 }]" [total]="8" text="6 of 8 decided" />
 * Whatever the segments leave of the total is the empty, not-reviewed remainder. The bar is hidden from assistive
 * technology; the text is the meter, in the type of the place it sits (Body in a table row, so its baseline is the row's).
 * The bar's width is the host's `--lx-meter-width` (120 px unless the screen sets it).
 */
@Component({
  selector: 'lx-meter',
  template: `
    <span class="lx-bar" aria-hidden="true">
      @for (run of runs(); track run.tone) { <span class="lx-run" [attr.data-tone]="run.tone" [style.width.%]="run.percent"></span> }
    </span>
    <span class="lx-meter-text">{{ text() }}</span>
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 12px; min-width: 0; }
    .lx-bar {
      flex: none; display: flex; width: var(--lx-meter-width, 120px); height: 4px; background: var(--p-lx-sunken); overflow: hidden;
      transform-origin: left center; animation: lx-meter-in 150ms var(--lx-ease) both;
    }
    @keyframes lx-meter-in { from { transform: scaleX(0); } }
    .lx-run { height: 100%; }
    .lx-run[data-tone='found'] { background: var(--p-lx-found); }
    .lx-run[data-tone='ambiguous'] { background: var(--p-lx-ink-2); opacity: .55; }
    .lx-run[data-tone='missing'] { background: var(--p-lx-missing); }
    .lx-run[data-tone='decided'] { background: var(--p-lx-ink); }
    .lx-meter-text { color: var(--p-lx-ink-2); white-space: nowrap; }
  `,
})
export class Meter {
  readonly segments = input.required<readonly MeterSegment[]>();
  readonly total = input.required<number>();
  readonly text = input.required<string>();
  readonly runs = computed(() => meterRuns(this.segments(), this.total()));
}
