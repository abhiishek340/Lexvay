import { formatDate } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { OUTCOME_LABEL } from '../core/labels';
import { Outcome } from '../core/models';

let nextId = 0;

/** "Dana Whitfield" is "DW", "Dr. Priya Raman" is "PR": the first letters of the first and last names. */
export function initials(name: string): string {
  const words = name.replace(/^(dr|mr|mrs|ms|prof)\.?\s+/i, '').split(/\s+/).filter(Boolean);
  if (words.length === 0) { return ''; }
  const first = words[0].charAt(0);
  const last = words.length > 1 ? words[words.length - 1].charAt(0) : '';
  return `${first}${last}`.toUpperCase();
}

/**
 * The mark of a signed determination (DESIGN.md "Seal"): a ring in the seal colour with the outcome and the
 * signing date set around it and the signer's initials in the middle.
 *   <lx-seal outcome="DOES_NOT_MEET" signer="Dana Whitfield" signedAt="2026-11-27T09:12:00Z" [size]="96" />
 * The accessible name says it all in one sentence. With `stamp`, the seal lands when it first appears (DESIGN.md
 * "Signing"): it scales from 1.12 and turns from -8 degrees on the spring, and one ink ring spreads and fades. Once,
 * never looped; reduced motion shows the seal at rest.
 */
@Component({
  selector: 'lx-seal',
  template: `
    <svg viewBox="0 0 100 100" [attr.width]="size()" [attr.height]="size()" aria-hidden="true">
      <defs><path [attr.id]="pathId" d="M 50 50 m -40 0 a 40 40 0 1 1 80 0 a 40 40 0 1 1 -80 0" /></defs>
      @if (stamp()) { <circle cx="50" cy="50" r="48" class="lx-seal-ink" stroke-width="3" /> }
      <circle cx="50" cy="50" r="48" class="lx-seal-ring" stroke-width="1.6" />
      <circle cx="50" cy="50" r="32" class="lx-seal-ring" stroke-width="0.8" />
      <text class="lx-seal-legend"><textPath [attr.href]="'#' + pathId" startOffset="50%" text-anchor="middle">{{ legend() }}</textPath></text>
      <text x="50" y="50" class="lx-seal-initials" text-anchor="middle" dominant-baseline="central">{{ letters() }}</text>
    </svg>
  `,
  host: { role: 'img', '[attr.aria-label]': 'description()', '[class.lx-seal-stamp]': 'stamp()' },
  styles: `
    :host { display: inline-block; line-height: 0; color: var(--p-lx-seal); }
    svg { overflow: visible; }
    .lx-seal-ring { fill: none; stroke: currentColor; }
    :host(.lx-seal-stamp) { animation: lx-seal-stamp 420ms var(--lx-spring) both; }
    @keyframes lx-seal-stamp { from { transform: scale(1.12) rotate(-8deg); opacity: 0.4; } }
    .lx-seal-ink {
      fill: none; stroke: currentColor; opacity: 0; transform-box: fill-box; transform-origin: center;
      animation: lx-seal-ink 600ms var(--lx-ease-out) 140ms both;
    }
    @keyframes lx-seal-ink { from { opacity: 0.45; transform: scale(1); } to { opacity: 0; transform: scale(1.32); } }
    text { fill: currentColor; font-family: var(--lx-font-sans); }
    .lx-seal-legend { font-size: 8.4px; font-weight: 600; letter-spacing: 0.9px; text-transform: uppercase; }
    .lx-seal-initials { font-size: 24px; font-weight: 650; letter-spacing: -0.5px; }
  `,
})
export class Seal {
  readonly outcome = input.required<Outcome>();
  readonly signer = input.required<string>();
  readonly signedAt = input.required<string>();
  readonly size = input(96);
  /** Plays the stamp when the seal appears: the signing moment, nowhere else. */
  readonly stamp = input(false);
  readonly pathId = `lx-seal-path-${nextId++}`;

  readonly date = computed(() => formatDate(this.signedAt(), 'd MMM y', 'en-US'));
  readonly letters = computed(() => initials(this.signer()));
  readonly legend = computed(() => `${OUTCOME_LABEL[this.outcome()]} · ${this.date()}`);
  readonly description = computed(() => `Signed by ${this.signer()} on ${this.date()}: ${OUTCOME_LABEL[this.outcome()].toLowerCase()}`);
}
