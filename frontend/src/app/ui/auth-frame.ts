import { Component, ViewEncapsulation, input } from '@angular/core';
import { Mark } from './mark';

/**
 * The page a person sees before signing in (DESIGN.md "Sign in"): the mark and the form centred on the right, and,
 * with `story`, the projected `[lxAuthStory]` stage on the left (hidden under 900 px). Sign-in shows its map story;
 * choosing a password shows the form alone. The form vocabulary inside (`lx-auth-field`, labels, the heading) is
 * styled here once, so both pages read the same; the encapsulation is off because that content is projected.
 */
@Component({
  selector: 'lx-auth-frame',
  imports: [Mark],
  encapsulation: ViewEncapsulation.None,
  host: { class: 'lx-auth', '[class.lx-auth--story]': 'story()' },
  template: `
    @if (story()) { <ng-content select="[lxAuthStory]" /> }
    <section class="lx-auth-side">
      <div class="lx-auth-card">
        <div class="lx-auth-brand"><lx-mark [size]="32" /><span class="lx-auth-wordmark">Lexvay</span></div>
        <ng-content />
      </div>
    </section>
  `,
  styles: `
    .lx-auth { display: grid; grid-template-columns: minmax(0, 1fr); min-height: 100dvh; background: var(--p-lx-surface); }
    .lx-auth--story { grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); }
    .lx-auth-side { display: grid; place-items: center; padding: 48px 24px; }
    .lx-auth-card { width: 100%; max-width: 360px; animation: lx-rise var(--lx-t-3) var(--lx-ease-out) backwards; }
    .lx-auth-brand { display: flex; align-items: center; gap: 10px; margin-bottom: 40px; }
    .lx-auth-wordmark { font-size: 19px; font-weight: 650; letter-spacing: -0.02em; }
    .lx-auth-card h1 { margin-bottom: 24px; }
    .lx-auth-card form { display: flex; flex-direction: column; gap: 20px; }
    .lx-auth-field { display: flex; flex-direction: column; gap: 6px; }
    .lx-auth-field label { font-weight: 550; font-size: 13px; line-height: 20px; }
    .lx-auth-card form p-button { margin-top: 4px; }
    @media (max-width: 899px) {
      .lx-auth--story { grid-template-columns: minmax(0, 1fr); }
    }
  `,
})
export class AuthFrame {
  /** Whether the page has a stage on the left, projected with the `lxAuthStory` attribute. */
  readonly story = input(false);
}
