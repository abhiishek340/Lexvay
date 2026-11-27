import { Component, input, signal } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { Popover } from 'primeng/popover';

/**
 * The explanation a screen would otherwise print as a paragraph, behind a 20 px info button (DESIGN.md "InfoTip"):
 *   <lx-info-tip label="About demo data">Northfield University and everything in it is invented …</lx-info-tip>
 * `label` names the button and the popover. The content is short prose; one or two sentences.
 */
@Component({
  selector: 'lx-info-tip',
  imports: [NgIcon, Popover],
  template: `
    <button type="button" class="lx-info-tip" [attr.aria-label]="label()" [attr.aria-expanded]="open()" aria-haspopup="dialog" (click)="tip.toggle($event)">
      <ng-icon name="phosphorInfo" size="16" aria-hidden="true" />
    </button>
    <p-popover #tip [ariaLabel]="label()" (onShow)="open.set(true)" (onHide)="open.set(false)" styleClass="lx-info-tip-panel">
      <div class="lx-info-tip-body"><ng-content /></div>
    </p-popover>
  `,
  styles: `
    :host { display: inline-flex; vertical-align: middle; }
    .lx-info-tip {
      width: 20px; height: 20px; padding: 0; border: 0; border-radius: 999px; display: inline-grid; place-items: center;
      background: transparent; color: var(--p-lx-text-2); cursor: pointer;
      transition: background-color var(--lx-t-1) var(--lx-ease-out), color var(--lx-t-1) var(--lx-ease-out);
    }
    .lx-info-tip:hover, .lx-info-tip[aria-expanded='true'] { background: var(--p-lx-surface-2); color: var(--p-lx-text); }
    .lx-info-tip-body { max-width: 18rem; font-size: 13px; line-height: 20px; color: var(--p-lx-text); }
  `,
})
export class InfoTip {
  readonly label = input.required<string>();
  readonly open = signal(false);
}
