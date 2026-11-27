import { Component } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { phosphorCheckCircle, phosphorWarning } from '@ng-icons/phosphor-icons/regular';
import auraToast from '@primeuix/themes/aura/toast';
import { Toast } from 'primeng/toast';
import { registerComponentTokens } from '../theme/component-tokens';
import { lexvayTokens as t } from '../theme/lexvay-preset';

export const SHELL_TOAST_ICONS = { phosphorCheckCircle, phosphorWarning };

/** A toast is a raised card (e3) whose icon carries the severity; the words stay in text colours. */
const severity = {
  background: t.raised, borderColor: 'transparent', color: t.text, detailColor: t.text2, shadow: t.e3,
  closeButton: { hoverBackground: t.surface2, focusRing: { color: t.accent, shadow: 'none' } },
};
const TOAST_TOKENS = {
  toast: {
    ...auraToast,
    root: { ...auraToast.root, width: '24rem', borderRadius: '12px', borderWidth: '0', blur: '0' },
    content: { ...auraToast.content, padding: '0', gap: '0.625rem' },
    info: severity, success: severity, warn: severity, error: severity,
  },
};

/** Results of long operations, bottom right; the shell defers it until the page is idle with its icons and tokens. */
@Component({
  selector: 'lx-shell-toast',
  imports: [NgIcon, Toast],
  template: `
    <p-toast position="bottom-right" [life]="5000">
      <ng-template #message let-message>
        <div class="lx-toast" [attr.data-severity]="message.severity">
          <ng-icon [name]="message.severity === 'error' ? 'phosphorWarning' : message.severity === 'success' ? 'phosphorCheckCircle' : 'phosphorInfo'" size="20" aria-hidden="true" />
          <div>
            <p class="lx-toast-summary">{{ message.summary }}</p>
            @if (message.detail) { <p class="lx-toast-detail">{{ message.detail }}</p> }
          </div>
        </div>
      </ng-template>
    </p-toast>
  `,
  styles: `
    .lx-toast { display: flex; gap: 12px; align-items: flex-start; }
    .lx-toast ng-icon { flex: none; margin-top: 1px; color: var(--p-lx-accent); }
    .lx-toast[data-severity='success'] ng-icon { color: var(--p-lx-found); }
    .lx-toast[data-severity='error'] ng-icon { color: var(--p-lx-missing); }
    .lx-toast[data-severity='warn'] ng-icon { color: var(--p-lx-ambiguous); }
    .lx-toast-summary { font-weight: 600; color: var(--p-lx-text); }
    .lx-toast-detail { color: var(--p-lx-text-2); font-size: 13px; line-height: 20px; margin-top: 2px; }
  `,
  viewProviders: [provideIcons(SHELL_TOAST_ICONS)],
})
export class ShellToast {
  constructor() {
    registerComponentTokens(TOAST_TOKENS);
  }
}
