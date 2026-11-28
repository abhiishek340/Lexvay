import { Component } from '@angular/core';
import auraToast from '@primeuix/themes/aura/toast';
import { Toast } from 'primeng/toast';
import { registerComponentTokens } from '../theme/component-tokens';
import { lexvayTokens as t } from '../theme/lexvay-preset';

/** A toast is an overlay (surface, the overlay elevation) with words only; an error says so in `missing`. */
const severity = {
  background: t.surface, borderColor: 'transparent', color: t.ink, detailColor: t.ink2, shadow: t.overlay,
  closeButton: { hoverBackground: t.sunken, focusRing: { color: t.accent, shadow: 'none' } },
};
const TOAST_TOKENS = {
  toast: {
    ...auraToast,
    root: { ...auraToast.root, width: '22rem', borderRadius: '8px', borderWidth: '0', blur: '0' },
    content: { ...auraToast.content, padding: '0', gap: '0' },
    info: severity, success: severity, warn: severity, error: severity,
  },
};

/** Results of long operations, bottom right, for 4 s (DESIGN.md "States and feedback"); deferred with its tokens. */
@Component({
  selector: 'lx-shell-toast',
  imports: [Toast],
  template: `
    <p-toast position="bottom-right" [life]="4000">
      <ng-template #message let-message>
        <div class="lx-toast" [attr.data-severity]="message.severity">
          <p class="lx-toast-summary">{{ message.summary }}</p>
          @if (message.detail) { <p class="lx-toast-detail">{{ message.detail }}</p> }
        </div>
      </ng-template>
    </p-toast>
  `,
  styles: `
    .lx-toast { padding: 12px 16px; }
    .lx-toast-summary { font-size: 13px; line-height: 20px; font-weight: 600; }
    .lx-toast[data-severity='error'] .lx-toast-summary { color: var(--p-lx-missing); }
    .lx-toast-detail { color: var(--p-lx-ink-2); font-size: 13px; line-height: 20px; }
  `,
})
export class ShellToast {
  constructor() {
    registerComponentTokens(TOAST_TOKENS);
  }
}
