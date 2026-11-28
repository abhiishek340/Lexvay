import { Component, ElementRef, afterRenderEffect, inject, input } from '@angular/core';
import { Tab, TabList } from 'primeng/tabs';

/** A state's progress as its tab shows it ("3 of 8"); `label` says it in words (the tab's description). */
export interface StateProgress { readonly value: number; readonly total: number; readonly label: string }

/** One target state as a tab: its code, full name and, once known, its progress. */
export interface StateTab { readonly code: string; readonly name: string; readonly progress: StateProgress | null }

/**
 * The target states as an underlined tab row (DESIGN.md "Review" header), shared by Review and Coverage: state names in
 * UI type, the active one in ink over a 2 px ink bar, the progress as "3 of 8" meta text after each name. It is the tab
 * list of the screen's own PrimeNG `Tabs`, so it must sit inside a `p-tabs` whose `value` is the state code; the screen
 * keeps the panels. Each tab is named by the state's full name alone; its progress is the tab's description, through
 * hidden text outside the tab.
 *   <p-tabs [value]="code()" (valueChange)="select($event)" [selectOnFocus]="true" [scrollable]="true" [showNavigators]="false">
 *     <lx-state-tabs [states]="tabs()" [active]="code()" />
 */
@Component({
  selector: 'lx-state-tabs',
  imports: [Tab, TabList],
  template: `
    <p-tablist class="lx-state-tablist">
      @for (s of states(); track s.code) {
        <p-tab [value]="s.code" class="lx-state-tab" [attr.aria-describedby]="s.progress ? 'lx-state-progress-' + s.code : null">
          <span>{{ s.name }}</span>
          @if (s.progress; as p) { <span class="lx-state-count" aria-hidden="true">{{ p.value }} of {{ p.total }}</span> }
        </p-tab>
      }
    </p-tablist>
    <div hidden>
      @for (s of states(); track s.code) {
        @if (s.progress; as p) { <span [id]="'lx-state-progress-' + s.code">{{ p.label }}</span> }
      }
    </div>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .lx-state-tab.p-tab { display: inline-flex; align-items: baseline; gap: 6px; font-size: 13px; line-height: 20px; white-space: nowrap; }
    .lx-state-count { font-size: 12px; line-height: 16px; font-weight: 500; letter-spacing: 0.01em; color: var(--p-lx-ink-2); }
  `,
})
export class StateTabs {
  readonly states = input.required<readonly StateTab[]>();
  /** The selected state's code (the same as the surrounding Tabs' value). */
  readonly active = input.required<string>();

  constructor() {
    // A state opened from a link may sit beyond the edge of a narrow window: scroll the list (never the page) to its tab.
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    afterRenderEffect(() => {
      if (!this.active() || !this.states().length) { return; }
      const list = host.querySelector<HTMLElement>('.p-tablist-content');
      const tab = host.querySelector<HTMLElement>('.p-tab-active');
      if (!list || !tab) { return; }
      const box = list.getBoundingClientRect();
      const at = tab.getBoundingClientRect();
      if (at.left < box.left) { list.scrollLeft -= box.left - at.left; } else if (at.right > box.right) { list.scrollLeft += at.right - box.right; }
    });
  }
}
