import { Component, input } from '@angular/core';

/**
 * The Lexvay mark: an L whose foot rises into a tick, in paper on an ink tile (DESIGN.md "Shell": monochrome). Decorative
 * wherever it appears next to the wordmark; the wordmark (or the link around both) carries the name.
 *   <lx-mark [size]="20" />
 */
@Component({
  selector: 'lx-mark',
  template: `
    <svg viewBox="0 0 32 32" [attr.width]="size()" [attr.height]="size()" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="7" class="lx-mark-tile" />
      <path d="M11 8.5v14.5h4.5" class="lx-mark-stroke" />
      <path d="M15.5 23l7-9.5" class="lx-mark-stroke" />
    </svg>
  `,
  styles: `
    :host { display: inline-flex; flex: none; line-height: 0; }
    .lx-mark-tile { fill: var(--p-lx-ink); }
    .lx-mark-stroke { fill: none; stroke: var(--p-lx-paper); stroke-width: 3; stroke-linecap: round; stroke-linejoin: round; }
  `,
})
export class Mark {
  readonly size = input(20);
}
