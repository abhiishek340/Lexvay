import { Directive, input, numberAttribute } from '@angular/core';

/**
 * The first-view entrance of a list or grid item (DESIGN.md "Motion"): it fades in and rises 6 px over 320 ms,
 * 30 ms after the item before it, with at most ten staggered (the rest arrive with the tenth).
 *   @for (row of rows(); track row.id) { <li [lxEnter]="$index">…</li> }
 * Angular adds the `lx-enter` class only while the element enters (animate.enter), so a list that later changes
 * animates only its new items. Reduced motion zeroes the animation in styles.css; input is never delayed.
 */
@Directive({
  selector: '[lxEnter]',
  host: { 'animate.enter': 'lx-enter', '[style.--lx-i]': 'lxEnter()' },
})
export class Enter {
  /** The item's position in its list, for the stagger. */
  readonly lxEnter = input(0, { transform: numberAttribute });
}
