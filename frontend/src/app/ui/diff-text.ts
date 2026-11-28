import { Component, input } from '@angular/core';
import { DiffSegment } from '../core/models';

/**
 * A word or line diff as text: inserted runs in ins (underlined in `found`), deleted runs in del (struck through in
 * `missing`), never tinted blocks. Screen readers hear where each run starts and ends, so the change is
 * never carried by colour or by the line style alone.
 */
@Component({
  selector: 'lx-diff-text',
  // One line on purpose: whitespace between the elements would become part of the text.
  template: `@for (s of segments(); track $index) {@switch (s.op) {@case ('INSERT') {<ins>{{ s.text }}</ins>} @case ('DELETE') {<del>{{ s.text }}</del>} @default {<span>{{ s.text }}</span>}}}`,
  styles: `
    :host { white-space: pre-wrap; overflow-wrap: anywhere; }
    ins, del { text-decoration-thickness: 1px; text-underline-offset: 3px; }
    ins { text-decoration-line: underline; color: var(--p-lx-found); }
    del { text-decoration-line: line-through; color: var(--p-lx-missing); }
    ins::before, ins::after, del::before, del::after {
      position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap;
    }
    ins::before { content: ' [inserted: '; }
    del::before { content: ' [deleted: '; }
    ins::after, del::after { content: '] '; }
  `,
})
export class DiffText {
  readonly segments = input.required<readonly DiffSegment[]>();
}
