import { Component, computed, input } from '@angular/core';
import { FINDING_LABEL } from '../core/labels';
import { JurisdictionSummary } from '../core/models';
import { barSegments } from '../core/overview';
import { FindingLabel } from './finding';

let nextId = 0;

/**
 * The stacked findings bar: found is solid, ambiguous is hatched, missing is cross-hatched, so the three
 * segments differ by pattern as well as colour; the adjacent legend gives the counts in words.
 */
@Component({
  selector: 'lx-findings-bar',
  imports: [FindingLabel],
  template: `
    <svg class="lx-bar" width="100%" height="10" role="img" [attr.aria-label]="description()" preserveAspectRatio="none">
      <defs>
        <pattern [attr.id]="id + '-hatch'" width="4" height="4" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <rect width="4" height="4" style="fill: var(--p-lx-ambiguous-tint)" />
          <rect width="2" height="4" style="fill: var(--p-lx-ambiguous)" />
        </pattern>
        <pattern [attr.id]="id + '-cross'" width="4" height="4" patternUnits="userSpaceOnUse">
          <rect width="4" height="4" style="fill: var(--p-lx-missing)" />
          <rect x="1" y="1" width="2" height="2" style="fill: var(--p-lx-missing-tint)" />
        </pattern>
      </defs>
      @for (seg of placed(); track seg.finding) {
        @if (seg.count > 0) {
          <rect [attr.x]="seg.x + '%'" y="0" [attr.width]="seg.percent + '%'" height="10" [attr.fill]="fill(seg.finding)"
                [style.fill]="seg.finding === 'EVIDENCE_FOUND' ? 'var(--p-lx-found)' : null" />
        }
      }
    </svg>
    <ul class="lx-legend">
      @for (seg of placed(); track seg.finding) {
        <li><span class="lx-legend-count lx-mono">{{ seg.count }}</span> <lx-finding [finding]="seg.finding" /></li>
      }
    </ul>
  `,
  styles: `
    :host { display: block; min-width: 180px; }
    .lx-bar { display: block; border-radius: 2px; background: var(--p-lx-surface-2); outline: 1px solid var(--p-lx-line); outline-offset: -1px; }
    .lx-legend { list-style: none; margin: 6px 0 0; padding: 0; display: flex; flex-wrap: wrap; gap: 2px 14px; font-size: 12px; line-height: 16px; }
    .lx-legend li { display: inline-flex; align-items: center; gap: 4px; }
    .lx-legend-count { color: var(--p-lx-text); }
  `,
})
export class FindingsBar {
  readonly summary = input.required<JurisdictionSummary>();
  readonly id = `lx-bar-${nextId++}`;

  readonly placed = computed(() => {
    let x = 0;
    return barSegments(this.summary()).map((s) => {
      const placed = { ...s, x };
      x += s.percent;
      return placed;
    });
  });

  readonly description = computed(() => {
    const s = this.summary();
    return `${s.evidenceFound} ${FINDING_LABEL.EVIDENCE_FOUND.toLowerCase()}, ${s.ambiguous} ${FINDING_LABEL.AMBIGUOUS.toLowerCase()}, `
      + `${s.evidenceMissing} ${FINDING_LABEL.EVIDENCE_MISSING.toLowerCase()}, of ${s.total} requirements`;
  });

  fill(finding: string): string | null {
    if (finding === 'AMBIGUOUS') { return `url(#${this.id}-hatch)`; }
    if (finding === 'EVIDENCE_MISSING') { return `url(#${this.id}-cross)`; }
    return null;
  }
}
