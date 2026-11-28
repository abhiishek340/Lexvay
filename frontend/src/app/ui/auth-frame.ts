import { Component, ViewEncapsulation } from '@angular/core';
import { Mark } from './mark';

/**
 * The page a person sees before signing in (DESIGN.md "Sign in"): one 360 px column centred on paper, the mark and the
 * wordmark above the form. No art, no map, no slogan. Sign-in and choosing a password share it; the form vocabulary
 * inside (`lx-auth-field`, labels, the heading) is styled here once, so both pages read the same, and the encapsulation
 * is off because that content is projected.
 */
@Component({
  selector: 'lx-auth-frame',
  imports: [Mark],
  encapsulation: ViewEncapsulation.None,
  host: { class: 'lx-auth' },
  template: `
    <div class="lx-auth-column">
      <div class="lx-auth-brand"><lx-mark [size]="24" /><span class="lx-auth-wordmark">Lexvay</span></div>
      <ng-content />
    </div>
  `,
  styles: `
    .lx-auth { display: grid; place-items: center; min-height: 100dvh; box-sizing: border-box; padding: 56px 20px; background: var(--p-lx-paper); }
    .lx-auth-column { width: 100%; max-width: 360px; }
    .lx-auth-brand { display: flex; align-items: center; gap: 8px; margin-bottom: 40px; }
    .lx-auth-wordmark { font-size: 15px; line-height: 24px; font-weight: 600; letter-spacing: -0.004em; }
    .lx-auth-column h1 { margin-bottom: 24px; }
    .lx-auth-column form { display: flex; flex-direction: column; gap: 16px; }
    .lx-auth-field { display: flex; flex-direction: column; gap: 6px; }
    .lx-auth-field label { font-size: 13px; line-height: 20px; font-weight: 500; }
    .lx-auth-column form p-button { margin-top: 8px; }
  `,
})
export class AuthFrame {}
