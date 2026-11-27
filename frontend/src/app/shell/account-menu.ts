import { Component, inject, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { phosphorMonitor, phosphorMoon, phosphorSignOut, phosphorSun } from '@ng-icons/phosphor-icons/regular';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Popover } from 'primeng/popover';
import { SelectButton } from 'primeng/selectbutton';
import { ROLE_LABEL } from '../core/labels';
import { ProgramContext } from '../core/program-context';
import { SessionService } from '../core/session.service';
import { Theme, ThemeService } from '../core/theme.service';
import { registerComponentTokens } from '../theme/component-tokens';
import { SEGMENTED_TOKENS } from '../theme/segmented-tokens';
import type { IconName } from '../theme/icons';

export const ACCOUNT_MENU_ICONS = { phosphorMonitor, phosphorMoon, phosphorSignOut, phosphorSun };

/**
 * The avatar menu (DESIGN.md "Shell"): who is signed in, the colour theme (System, Light, Dark) and Sign out.
 * The shell owns the avatar button and calls toggle(); this popover loads in a deferred chunk with its icons and
 * the segmented control's tokens.
 */
@Component({
  selector: 'lx-account-menu',
  imports: [FormsModule, NgIcon, ButtonDirective, ButtonLabel, Popover, SelectButton],
  template: `
    <p-popover #menu ariaLabel="Account" styleClass="lx-account" (onShow)="opened.emit(true)" (onHide)="opened.emit(false)">
      @if (session.user(); as user) {
        <div class="lx-account-who">
          <p class="lx-account-name">{{ user.displayName }}</p>
          <p class="lx-account-meta">{{ roleLabel[user.role] }} · {{ user.email }}</p>
        </div>
        <div class="lx-account-theme">
          <span id="theme-label" class="lx-account-label">Theme</span>
          <p-selectbutton [options]="themes" optionLabel="label" optionValue="value" [allowEmpty]="false" size="small"
                          [ngModel]="theme.theme()" (ngModelChange)="theme.set($event)" ariaLabelledBy="theme-label">
            <ng-template #item let-option>
              <ng-icon [name]="option.icon" size="16" aria-hidden="true" />
              <span>{{ option.label }}</span>
            </ng-template>
          </p-selectbutton>
        </div>
        <button pButton type="button" [text]="true" severity="secondary" class="lx-account-out" (click)="signOut()">
          <ng-icon name="phosphorSignOut" size="18" aria-hidden="true" />
          <span pButtonLabel>Sign out</span>
        </button>
      }
    </p-popover>
  `,
  styles: `
    .lx-account-who { padding: 2px 4px 12px; border-bottom: 1px solid var(--p-lx-line); }
    .lx-account-name { font-weight: 600; font-size: 15px; line-height: 22px; }
    .lx-account-meta { font-size: 13px; line-height: 20px; color: var(--p-lx-text-2); }
    .lx-account-theme { display: flex; flex-direction: column; gap: 8px; padding: 12px 4px; border-bottom: 1px solid var(--p-lx-line); }
    .lx-account-label { font-size: 13px; line-height: 20px; font-weight: 500; color: var(--p-lx-text-2); }
    .lx-account-out { width: 100%; justify-content: flex-start; margin-top: 8px; }
    :host ::ng-deep .p-togglebutton-content { gap: 6px; }
  `,
  viewProviders: [provideIcons(ACCOUNT_MENU_ICONS)],
})
export class AccountMenu {
  readonly session = inject(SessionService);
  readonly theme = inject(ThemeService);
  private readonly context = inject(ProgramContext);
  private readonly router = inject(Router);
  private readonly menu = viewChild.required<Popover>('menu');
  readonly opened = output<boolean>();
  readonly roleLabel = ROLE_LABEL;
  readonly themes: { value: Theme; label: string; icon: IconName }[] = [
    { value: 'system', label: 'System', icon: 'phosphorMonitor' },
    { value: 'light', label: 'Light', icon: 'phosphorSun' },
    { value: 'dark', label: 'Dark', icon: 'phosphorMoon' },
  ];

  constructor() {
    registerComponentTokens(SEGMENTED_TOKENS);
  }

  toggle(event: Event): void {
    this.menu().toggle(event);
  }

  async signOut(): Promise<void> {
    this.menu().hide();
    await this.session.logout();
    this.context.clear();
    await this.router.navigate(['/login']);
  }
}
