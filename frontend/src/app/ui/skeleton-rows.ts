import { Component, computed, input } from '@angular/core';
import { Skeleton } from 'primeng/skeleton';
import { registerComponentTokens } from '../theme/component-tokens';
import { lexvaySkeleton } from '../theme/skeleton-tokens';

/**
 * A list or table while it loads (DESIGN.md "States and feedback", D-496): rows the height of the real rows (40 px by
 * default) in sunken, a hairline of paper between them, no shimmer. The container is one named status that is busy;
 * the bars are hidden from assistive technology.
 *   <lx-skeleton-rows label="Loading the audit trail" [rows]="8" />
 */
@Component({
  selector: 'lx-skeleton-rows',
  imports: [Skeleton],
  template: `
    @for (row of list(); track row) { <p-skeleton aria-hidden="true" [height]="height() + 'px'" /> }
  `,
  host: { 'role': 'status', 'aria-busy': 'true', '[attr.aria-label]': 'label()' },
  styles: `:host { display: flex; flex-direction: column; gap: 1px; }`,
})
export class SkeletonRows {
  /** What is loading, as the status names it: "Loading the audit trail". */
  readonly label = input.required<string>();
  readonly rows = input(5);
  /** The height of one real row in px. */
  readonly height = input(40);

  readonly list = computed(() => Array.from({ length: this.rows() }, (_, i) => i));

  constructor() {
    registerComponentTokens({ skeleton: lexvaySkeleton });
  }
}
