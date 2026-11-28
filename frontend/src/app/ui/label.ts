import { Component } from '@angular/core';

/**
 * A compact attribute that must stand apart in a dense row ("Required", "Elective", a role), as DESIGN.md "Label" draws
 * it: 20 px high, a 1 px line-strong border, ink-2 meta text, no fill and no icon. Elsewhere attributes are plain text
 * joined with " · ".
 *   <lx-label>Required</lx-label>
 */
@Component({
  selector: 'lx-label',
  template: `<ng-content />`,
  styles: `
    :host {
      display: inline-flex; align-items: center; height: 20px; box-sizing: border-box; padding: 0 6px; flex: none;
      border: 1px solid var(--p-lx-line-strong); border-radius: var(--lx-r-control);
      font-size: 12px; line-height: 16px; font-weight: 500; letter-spacing: 0.01em; color: var(--p-lx-ink-2); white-space: nowrap;
    }
  `,
})
export class Label {}
