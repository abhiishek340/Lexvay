import { Component, input, signal } from '@angular/core';
import { Popover } from 'primeng/popover';

/** One definition in a screen's Help: the term in 600, what it means in one or two plain sentences. */
export interface HelpTerm { readonly term: string; readonly definition: string }

/**
 * The one help affordance a screen may have (DESIGN.md "Help"): a ghost "?" button at the end of the title row that
 * opens the screen's definitions as a short list. Explanations live here, never as paragraphs on the page or as an
 * info icon beside a heading.
 *   <lx-help [terms]="help" />
 */
@Component({
  selector: 'lx-help',
  imports: [Popover],
  template: `
    <button type="button" class="lx-help-button" aria-label="Help" aria-haspopup="dialog" [attr.aria-expanded]="open()" (click)="panel.toggle($event)">?</button>
    <p-popover #panel ariaLabel="Help" (onShow)="open.set(true)" (onHide)="open.set(false)">
      <dl class="lx-help-list">
        @for (t of terms(); track t.term) {
          <div><dt>{{ t.term }}</dt><dd>{{ t.definition }}</dd></div>
        }
      </dl>
    </p-popover>
  `,
  styles: `
    :host { display: inline-flex; flex: none; }
    .lx-help-button {
      width: 32px; height: 32px; padding: 0; border: 0; border-radius: var(--lx-r-control); background: transparent; cursor: pointer;
      font: inherit; font-size: 13px; line-height: 20px; font-weight: 500; color: var(--p-lx-ink-2);
      transition: background-color var(--lx-t-fast) var(--lx-ease), color var(--lx-t-fast) var(--lx-ease);
    }
    .lx-help-button:hover, .lx-help-button[aria-expanded='true'] { background: var(--p-lx-sunken); color: var(--p-lx-ink); }
    .lx-help-list { width: min(20rem, calc(100vw - 48px)); display: flex; flex-direction: column; gap: 12px; font-size: 13px; line-height: 20px; }
    .lx-help-list dt { font-weight: 600; color: var(--p-lx-ink); }
    .lx-help-list dd { margin: 2px 0 0; color: var(--p-lx-ink-2); }
  `,
})
export class Help {
  readonly terms = input.required<readonly HelpTerm[]>();
  readonly open = signal(false);
}
