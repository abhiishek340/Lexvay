import { Component, input } from '@angular/core';

/** The seven 10 px shapes of DESIGN.md "Status marks". Found and missing differ in shape as well as colour. */
export type MarkShape = 'disc' | 'square' | 'half' | 'ring' | 'ring-dot' | 'dashed-ring' | 'open-square';

/**
 * One 10 px mark drawn in the current colour, hidden from assistive technology: the shape alone, for StatusMark and for
 * the places that use the same shapes for something other than a review status (Coverage's cells and flags). The words
 * that say what it means always travel beside it.
 *   <lx-mark-shape shape="dashed-ring" />
 * `outdated` draws the shape inside the dashed ring of the outdated mark, scaled up around it: the same 1.5 px stroke and
 * the same eight dashes (the dash pattern grows with the radius), 2.5 px clear of the shape, for evidence that is partly
 * current and partly on a superseded version. The mark grows to 18 px.
 */
@Component({
  selector: 'lx-mark-shape',
  template: `
    <svg [attr.viewBox]="outdated() ? '-4 -4 18 18' : '0 0 10 10'" [attr.width]="outdated() ? 18 : 10" [attr.height]="outdated() ? 18 : 10"
         aria-hidden="true" focusable="false">
      @if (outdated()) { <circle cx="5" cy="5" r="8.25" class="lx-stroke" stroke-dasharray="3.88 2.6" /> }
      @switch (shape()) {
        @case ('disc') { <circle cx="5" cy="5" r="5" class="lx-fill" /> }
        @case ('square') { <rect x="0.5" y="0.5" width="9" height="9" rx="1" class="lx-fill" /> }
        @case ('half') { <circle cx="5" cy="5" r="4.25" class="lx-stroke" /><path d="M5 .75a4.25 4.25 0 0 0 0 8.5z" class="lx-fill" /> }
        @case ('ring') { <circle cx="5" cy="5" r="4.25" class="lx-stroke" /> }
        @case ('ring-dot') { <circle cx="5" cy="5" r="4.25" class="lx-stroke" /><circle cx="5" cy="5" r="1.75" class="lx-fill" /> }
        @case ('dashed-ring') { <circle cx="5" cy="5" r="4.25" class="lx-stroke" stroke-dasharray="2 1.34" /> }
        @case ('open-square') { <rect x="1" y="1" width="8" height="8" rx="1" class="lx-stroke" /> }
      }
    </svg>
  `,
  host: { '[attr.data-shape]': 'shape()', '[attr.data-outdated]': 'outdated() || null', 'aria-hidden': 'true' },
  styles: `
    :host { display: inline-flex; flex: none; line-height: 0; }
    svg { overflow: visible; }
    .lx-fill { fill: currentColor; }
    .lx-stroke { fill: none; stroke: currentColor; stroke-width: 1.5; }
  `,
})
export class MarkShapeIcon {
  readonly shape = input.required<MarkShape>();
  /** Draws the shape inside a dashed ring: some of the evidence it stands for is outdated. */
  readonly outdated = input(false);
}
