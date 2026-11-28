import { Component, ElementRef, input, signal, viewChild } from '@angular/core';
import { Popover } from 'primeng/popover';

/** One definition in a screen's Help: the term in 600, what it means in one or two plain sentences. */
export interface HelpTerm { readonly term: string; readonly definition: string }

/** One keyboard shortcut in a screen's Help: the keys as they are pressed and what they do. */
export interface HelpKey { readonly keys: string; readonly description: string }

/**
 * The one help affordance a screen may have (DESIGN.md "Help"): a ghost "?" button at the end of the title row that
 * opens the screen's definitions as a short list, and the screen's keyboard shortcuts under them when it has any.
 * Explanations live here, never as paragraphs on the page or as an info icon beside a heading.
 *   <lx-help [terms]="help" [keys]="shortcuts" />
 * `show()` opens it from a key press (Review's "?"), anchored to the button as a click would.
 */
@Component({
  selector: 'lx-help',
  imports: [Popover],
  template: `
    <button #button type="button" class="lx-help-button" aria-label="Help" aria-haspopup="dialog" [attr.aria-expanded]="open()" (click)="panel.toggle($event)">?</button>
    <p-popover #panel ariaLabel="Help" (onShow)="open.set(true)" (onHide)="open.set(false)">
      <div class="lx-help-body">
        <dl class="lx-help-list">
          @for (t of terms(); track t.term) {
            <div><dt>{{ t.term }}</dt><dd>{{ t.definition }}</dd></div>
          }
        </dl>
        @if (keys().length > 0) {
          <p class="lx-help-keys-title" id="lx-help-keys">Keys</p>
          <dl class="lx-help-keys" aria-labelledby="lx-help-keys">
            @for (k of keys(); track k.keys) {
              <div><dt><kbd>{{ k.keys }}</kbd></dt><dd>{{ k.description }}</dd></div>
            }
          </dl>
        }
      </div>
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
    .lx-help-body { width: min(20rem, calc(100vw - 48px)); font-size: 13px; line-height: 20px; }
    .lx-help-list { display: flex; flex-direction: column; gap: 12px; }
    .lx-help-list dt { font-weight: 600; color: var(--p-lx-ink); }
    .lx-help-list dd { margin: 4px 0 0; color: var(--p-lx-ink-2); }
    .lx-help-keys-title {
      margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--p-lx-line);
      font-size: 12px; line-height: 16px; font-weight: 500; letter-spacing: 0.01em; color: var(--p-lx-ink-2);
    }
    .lx-help-keys { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; }
    .lx-help-keys div { display: grid; grid-template-columns: 64px minmax(0, 1fr); align-items: baseline; }
    .lx-help-keys dd { margin: 0; color: var(--p-lx-ink); }
  `,
})
export class Help {
  readonly terms = input.required<readonly HelpTerm[]>();
  readonly keys = input<readonly HelpKey[]>([]);
  readonly open = signal(false);

  private readonly panel = viewChild.required<Popover>('panel');
  private readonly button = viewChild.required<ElementRef<HTMLButtonElement>>('button');

  /** Opens the list from the keyboard, under the button. */
  show(): void {
    if (!this.open()) {
      this.panel().show(null, this.button().nativeElement);
    }
  }
}
