import { Component, inject, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Popover } from 'primeng/popover';
import { SelectButton } from 'primeng/selectbutton';
import { ROLE_LABEL } from '../core/labels';
import { ProgramContext } from '../core/program-context';
import { SessionService } from '../core/session.service';
import { Theme, ThemeService } from '../core/theme.service';
import { registerComponentTokens } from '../theme/component-tokens';
import { SEGMENTED_TOKENS } from '../theme/segmented-tokens';

/**
 * The account menu (DESIGN.md "Shell"): who is signed in, the colour theme (System, Light, Dark) and Sign out, in words
 * alone. The shell owns the account button and calls toggle(); this popover loads in a deferred chunk with the
 * segmented control's tokens.
 */
@Component({
  selector: 'lx-account-menu',
  imports: [FormsModule, ButtonDirective, ButtonLabel, Popover, SelectButton],
  template: `
    <p-popover #menu ariaLabel="Account" (onShow)="opened.emit(true)" (onHide)="opened.emit(false)">
      @if (session.user(); as user) {
        <div class="lx-account-who">
          <p class="lx-account-name">{{ user.displayName }}</p>
          <p class="lx-meta">{{ roleLabel[user.role] }} · {{ user.email }}</p>
        </div>
        <div class="lx-account-theme">
          <span id="theme-label" class="lx-meta">Theme</span>
          <p-selectbutton [options]="themes" optionLabel="label" optionValue="value" [allowEmpty]="false"
                          [ngModel]="theme.theme()" (ngModelChange)="theme.set($event)" ariaLabelledBy="theme-label" />
        </div>
        <button pButton type="button" [text]="true" severity="secondary" class="lx-account-out" (click)="signOut()">
          <span pButtonLabel>Sign out</span>
        </button>
      }
    </p-popover>
  `,
  styles: `
    .lx-account-who { width: 15rem; padding: 0 0 12px; border-bottom: 1px solid var(--p-lx-line); }
    .lx-account-name { font-size: 13px; line-height: 20px; font-weight: 600; }
    .lx-account-who .lx-meta { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .lx-account-theme { display: flex; flex-direction: column; gap: 8px; padding: 12px 0; border-bottom: 1px solid var(--p-lx-line); }
    .lx-account-out { width: calc(100% + 16px); justify-content: flex-start; margin: 8px -8px 0; }
  `,
})
export class AccountMenu {
  readonly session = inject(SessionService);
  readonly theme = inject(ThemeService);
  private readonly context = inject(ProgramContext);
  private readonly router = inject(Router);
  private readonly menu = viewChild.required<Popover>('menu');
  readonly opened = output<boolean>();
  readonly roleLabel = ROLE_LABEL;
  readonly themes: { value: Theme; label: string }[] = [
    { value: 'system', label: 'System' },
    { value: 'light', label: 'Light' },
    { value: 'dark', label: 'Dark' },
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
