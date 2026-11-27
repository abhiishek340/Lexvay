import { formatDate } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { OUTCOME_LABEL } from '../core/labels';
import { Outcome } from '../core/models';
import { initials } from './initials';

/**
 * The mark of a signed determination (DESIGN.md "Seal"), as line art: a 40 px double ring in ink with the signer's
 * initials, and the signing date beneath it in meta type. It carries no outcome colour and never stamps or turns; it
 * fades in over 200 ms when it appears.
 *   <lx-seal outcome="DOES_NOT_MEET" signer="Dana Whitfield" signedAt="2026-11-27T09:12:00Z" />
 * `compact` draws it at 24 px without the date, for a 40 px table row whose next words name the signer and the date.
 * The accessible name says it all in one sentence. `preview` draws a seal that does not exist yet (the signing dialog
 * before the server has signed): no role and no name, hidden from assistive technology, so nothing claims a signature
 * that has not happened.
 */
@Component({
  selector: 'lx-seal',
  template: `
    <svg viewBox="0 0 40 40" [attr.width]="compact() ? 24 : 40" [attr.height]="compact() ? 24 : 40" aria-hidden="true" focusable="false">
      <circle cx="20" cy="20" r="19.25" class="lx-seal-ring" stroke-width="1.5" />
      <circle cx="20" cy="20" r="16.25" class="lx-seal-ring" stroke-width="0.75" />
      <text x="20" y="20.5" class="lx-seal-initials" [attr.font-size]="compact() ? 14 : 11" text-anchor="middle" dominant-baseline="central">{{ letters() }}</text>
    </svg>
    @if (!compact()) { <span class="lx-seal-date" aria-hidden="true">{{ date() }}</span> }
  `,
  host: {
    '[attr.role]': 'preview() ? null : "img"', '[attr.aria-label]': 'preview() ? null : description()',
    '[attr.aria-hidden]': 'preview() ? "true" : null',
  },
  styles: `
    :host { display: inline-flex; flex-direction: column; align-items: center; gap: 4px; flex: none; color: var(--p-lx-ink); animation: lx-seal-in 200ms var(--lx-ease) both; }
    @keyframes lx-seal-in { from { opacity: 0; } }
    svg { display: block; overflow: visible; }
    .lx-seal-ring { fill: none; stroke: currentColor; }
    .lx-seal-initials { fill: currentColor; font-family: var(--lx-font-sans); font-weight: 600; letter-spacing: 0.02em; }
    .lx-seal-date { font-size: 12px; line-height: 16px; font-weight: 500; letter-spacing: 0.01em; color: var(--p-lx-ink-2); white-space: nowrap; }
  `,
})
export class Seal {
  readonly outcome = input.required<Outcome>();
  readonly signer = input.required<string>();
  readonly signedAt = input.required<string>();
  readonly compact = input(false);
  readonly preview = input(false);

  readonly date = computed(() => formatDate(this.signedAt(), 'd MMM y', 'en-US'));
  readonly letters = computed(() => initials(this.signer()));
  readonly description = computed(() => `Signed by ${this.signer()} on ${this.date()}: ${OUTCOME_LABEL[this.outcome()].toLowerCase()}`);
}
