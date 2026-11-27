import { Component, input } from '@angular/core';
import { Tab, TabList } from 'primeng/tabs';
import { ProgressRing } from './progress-ring';

/** One state as a pill: its code, full name and, once known, the numbers of its 20 px ring. */
export interface StatePill {
  readonly code: string;
  readonly name: string;
  readonly progress: { readonly value: number; readonly total: number; readonly label: string } | null;
}

/**
 * The target states as pills (DESIGN.md "Review" header), shared by Review and Coverage. It is the tab list of the
 * screen's own PrimeNG `Tabs`, so it must sit inside a `p-tabs` whose `value` is the state code; the screen keeps the
 * panels. Each pill is a tab named by the state's full name, with a 20 px ring hidden from assistive technology whose
 * numbers are the tab's description, through hidden text outside the tab, so the name stays the name. The active pill
 * carries `view-transition-name: state-<code>`, the shared element a map tile morphs into.
 *   <p-tabs [value]="code()" (valueChange)="select($event)" [selectOnFocus]="true" [scrollable]="true" [showNavigators]="false">
 *     <lx-state-pills [states]="pills()" [active]="code()" />
 */
@Component({
  selector: 'lx-state-pills',
  imports: [Tab, TabList, ProgressRing],
  template: `
    <p-tablist class="lx-pills">
      @for (s of states(); track s.code) {
        <p-tab [value]="s.code" class="lx-pill" [style.view-transition-name]="s.code === active() ? 'state-' + s.code : null"
               [attr.aria-describedby]="s.progress ? 'lx-pill-progress-' + s.code : null">
          @if (s.progress; as p) {
            <span class="lx-pill-ring" aria-hidden="true"><lx-progress-ring [size]="20" [value]="p.value" [total]="p.total || 1" [label]="p.label" /></span>
          }
          <span>{{ s.name }}</span>
        </p-tab>
      }
    </p-tablist>
    <div hidden>
      @for (s of states(); track s.code) {
        @if (s.progress; as p) { <span [id]="'lx-pill-progress-' + s.code">{{ p.label }}</span> }
      }
    </div>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    :host ::ng-deep .lx-pills.p-tablist { background: transparent; border: 0; }
    :host ::ng-deep .lx-pills .p-tablist-content { gap: 4px; padding: 3px; }
    :host ::ng-deep .lx-pills .p-tablist-active-bar { display: none; }
    .lx-pill.p-tab {
      display: inline-flex; align-items: center; gap: 8px; height: 36px; margin: 0; padding: 0 14px 0 8px; border: 0; border-radius: var(--lx-r-pill);
      background: transparent; color: var(--p-lx-text-2); font-size: 14px; font-weight: 550; white-space: nowrap;
      transition: background-color var(--lx-t-1) var(--lx-ease-out), color var(--lx-t-1) var(--lx-ease-out), box-shadow var(--lx-t-1) var(--lx-ease-out);
    }
    .lx-pill.p-tab:not(.p-tab-active):hover { background: var(--p-lx-surface-2); color: var(--p-lx-text); }
    .lx-pill.p-tab-active { background: var(--p-lx-surface); color: var(--p-lx-text); box-shadow: var(--p-lx-e1); }
    .lx-pill-ring { display: inline-flex; }
  `,
})
export class StatePills {
  readonly states = input.required<readonly StatePill[]>();
  /** The selected state's code (the same as the surrounding Tabs' value). */
  readonly active = input.required<string>();
}
