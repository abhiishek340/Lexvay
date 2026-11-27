import { Component, computed, input } from '@angular/core';

export type RingTone = 'accent' | 'found' | 'ambiguous' | 'missing' | 'neutral' | 'seal';
export interface RingSegment { readonly tone: RingTone; readonly value: number }
export type RingSize = 20 | 40 | 96;

const STROKE: Record<RingSize, number> = { 20: 3, 40: 4, 96: 8 };

/** Segments in the order DESIGN.md fixes: found, ambiguous, missing, not reviewed. */
export function findingSegments(counts: { found: number; ambiguous: number; missing: number; notReviewed: number }): RingSegment[] {
  return [
    { tone: 'found', value: counts.found },
    { tone: 'ambiguous', value: counts.ambiguous },
    { tone: 'missing', value: counts.missing },
    { tone: 'neutral', value: counts.notReviewed },
  ];
}

/** Cumulative arc ends in percent of the ring, one per segment, in segment order. */
export function arcEnds(segments: readonly RingSegment[], total: number): number[] {
  let sum = 0;
  return segments.map((s) => {
    sum += Math.max(0, s.value);
    return total > 0 ? Math.min(100, (sum / total) * 100) : 0;
  });
}

/**
 * A progress ring at 20, 40 or 96 px. Either one value against a total (accent), or segments by status:
 *   <lx-progress-ring [value]="6" [total]="8" label="6 of 8 reviewed" />
 *   <lx-progress-ring [size]="96" [segments]="segments" [total]="8" label="5 found, 2 ambiguous, 1 missing">6 of 8</lx-progress-ring>
 * Projected content is the centre label (40 and 96 px). On first view each arc sweeps from 0 (600 ms, segment by
 * segment), and later changes move by stroke-dashoffset alone. `label` is the accessible name and states the numbers.
 */
@Component({
  selector: 'lx-progress-ring',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" [attr.viewBox]="viewBox()" aria-hidden="true">
      <circle class="lx-ring-track" [attr.cx]="centre()" [attr.cy]="centre()" [attr.r]="radius()" [attr.stroke-width]="stroke()" />
      @for (arc of arcs(); track arc.tone) {
        <circle class="lx-ring-arc" [class]="'lx-ring-' + arc.tone" [attr.cx]="centre()" [attr.cy]="centre()" [attr.r]="radius()"
                [attr.stroke-width]="stroke()" pathLength="100" [style.stroke-dashoffset]="100 - arc.end" [style.--lx-delay]="arc.delay + 'ms'"
                [attr.stroke-linecap]="round() ? 'round' : 'butt'" />
      }
    </svg>
    <span class="lx-ring-centre"><ng-content /></span>
  `,
  host: { role: 'img', '[attr.aria-label]': 'label()', '[attr.data-size]': 'size()', '[style.width.px]': 'size()', '[style.height.px]': 'size()' },
  styles: `
    :host { position: relative; display: inline-grid; place-items: center; flex: none; }
    svg { position: absolute; inset: 0; transform: rotate(-90deg); overflow: visible; }
    circle { fill: none; }
    .lx-ring-track { stroke: var(--p-lx-line); }
    .lx-ring-arc {
      stroke-dasharray: 100 200;
      transition: stroke-dashoffset var(--lx-t-4) var(--lx-ease-out);
      animation: lx-ring-sweep var(--lx-t-4) var(--lx-ease-out) var(--lx-delay, 0ms) backwards;
    }
    @keyframes lx-ring-sweep { from { stroke-dashoffset: 100; } }
    .lx-ring-accent { stroke: var(--p-lx-accent); }
    .lx-ring-found { stroke: var(--p-lx-found); }
    .lx-ring-ambiguous { stroke: var(--p-lx-ambiguous); }
    .lx-ring-missing { stroke: var(--p-lx-missing); }
    .lx-ring-neutral { stroke: var(--p-lx-neutral); }
    .lx-ring-seal { stroke: var(--p-lx-seal); }
    .lx-ring-centre { position: relative; font-weight: 600; font-variant-numeric: tabular-nums; text-align: center; line-height: 1.1; }
    :host([data-size="40"]) .lx-ring-centre { font-size: 11px; }
    :host([data-size="96"]) .lx-ring-centre { font-size: 20px; letter-spacing: -0.02em; }
    :host([data-size="20"]) .lx-ring-centre { display: none; }
  `,
})
export class ProgressRing {
  readonly size = input<RingSize>(40);
  /** One value against `total`; ignored when segments are given. */
  readonly value = input(0);
  readonly total = input(1);
  readonly segments = input<readonly RingSegment[] | null>(null);
  readonly label = input.required<string>();

  readonly stroke = computed(() => STROKE[this.size()]);
  readonly centre = computed(() => this.size() / 2);
  readonly radius = computed(() => (this.size() - this.stroke()) / 2);
  readonly viewBox = computed(() => `0 0 ${this.size()} ${this.size()}`);
  readonly round = computed(() => this.segments() === null);

  /**
   * Arcs drawn last segment first, each from the top to its cumulative end, so each earlier one covers the start of
   * the next: the visible part of every arc is its own segment, and only stroke-dashoffset ever moves.
   */
  readonly arcs = computed(() => {
    const segments = this.segments() ?? [{ tone: 'accent' as const, value: this.value() }];
    const ends = arcEnds(segments, this.total());
    return segments
      .map((s, i) => ({ tone: s.tone, end: ends[i], delay: i * 90 }))
      .filter((arc, i) => arc.end > (i > 0 ? ends[i - 1] : 0))
      .reverse();
  });
}
