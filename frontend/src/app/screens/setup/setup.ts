import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { phosphorCheckCircle, phosphorCircle, phosphorEye, phosphorEyeSlash } from '@ng-icons/phosphor-icons/regular';
import auraIconfield from '@primeuix/themes/aura/iconfield';
import auraInputtext from '@primeuix/themes/aura/inputtext';
import auraMetergroup from '@primeuix/themes/aura/metergroup';
import { MessageService } from 'primeng/api';
import { Button, ButtonDirective, ButtonLabel } from 'primeng/button';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputPassword } from 'primeng/inputpassword';
import { Message } from 'primeng/message';
import { MeterGroup, MeterItem } from 'primeng/metergroup';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { BUSY, Countdown, busyRetryAfter } from '../../core/busy';
import { problemDetail } from '../../core/problem';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { AuthFrame } from '../../ui/auth-frame';
import { StrengthLevel, passwordRules, passwordStrength } from './password-rules';

/** The icons the setup page shows beyond SHELL_ICONS, provided by its route (setup.routes.ts). */
export const SETUP_ICONS = { phosphorCheckCircle, phosphorCircle, phosphorEye, phosphorEyeSlash };

const STRENGTH_COLOUR: Record<StrengthLevel, string> = {
  0: 'var(--p-lx-missing)', 1: 'var(--p-lx-missing)', 2: 'var(--p-lx-ambiguous)', 3: 'var(--p-lx-found)', 4: 'var(--p-lx-found)',
};

/**
 * Choosing a password from a setup code (F5a): the sign-in frame without its story, two fields with show and hide,
 * the rules as a live checklist and a strength meter. The code arrives in the fragment (`/setup#code=…`), which the
 * browser never sends to a server; it is read once and removed from the address at once (history.replaceState), lives
 * only in this component, and leaves the page only in the POST (D-435). Nothing here is stored or logged. The server
 * signs nobody in, so success goes to sign-in.
 */
@Component({
  selector: 'lx-setup',
  imports: [FormsModule, RouterLink, NgIcon, Button, ButtonDirective, ButtonLabel, IconField, InputIcon, InputPassword, Message, MeterGroup, AuthFrame],
  templateUrl: './setup.html',
  styleUrl: './setup.css',
})
export class Setup {
  private readonly api = inject(ApiService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);
  /** The code, held for the POST; never in a signal the template could print, never in storage. */
  private readonly code: string;

  readonly password = signal('');
  readonly repeat = signal('');
  readonly masked = signal(true);
  readonly repeatMasked = signal(true);
  readonly invalid = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly wait = new Countdown(inject(DestroyRef));

  readonly rules = computed(() => passwordRules(this.password(), this.repeat()));
  readonly strength = computed(() => passwordStrength(this.password()));
  readonly meter = computed<MeterItem[]>(() => {
    const { level, label } = this.strength();
    return [{ label, value: level * 25, color: STRENGTH_COLOUR[level] }];
  });

  constructor() {
    registerComponentTokens({ iconfield: auraIconfield, inputtext: auraInputtext, message: lexvayMessage, metergroup: auraMetergroup });
    this.code = new URLSearchParams(location.hash.slice(1)).get('code') ?? '';
    history.replaceState(history.state, '', '/setup');
    this.invalid.set(this.code === '');
  }

  async submit(): Promise<void> {
    if (this.busy() || this.wait.remaining() > 0) { return; }
    if (this.rules().some((rule) => !rule.met)) {
      this.error.set('Meet every rule above.');
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    try {
      // The POST needs the CSRF cookie, which the session call sets.
      if (!this.session.loaded()) { await this.session.load(); }
      await firstValueFrom(this.api.setPassword(this.code, this.password()));
      this.password.set('');
      this.repeat.set('');
      await this.router.navigate(['/login']);
      this.toast.add({ severity: 'success', summary: 'Password set. Sign in.' });
    } catch (e: unknown) {
      this.fail(e);
    } finally {
      this.busy.set(false);
    }
  }

  private fail(e: unknown): void {
    const seconds = busyRetryAfter(e);
    if (seconds !== null) {
      this.wait.start(seconds);
      this.error.set(BUSY);
      return;
    }
    const detail = problemDetail(e, 'The password could not be set.');
    // A 400 either names a password rule (the code still works) or, for an unknown, used or expired code alike, says
    // "This setup code is not valid" (D-373): then the form has no use left.
    if (e instanceof HttpErrorResponse && e.status === 400 && !detail.startsWith('Password')) {
      this.invalid.set(true);
      return;
    }
    this.error.set(detail);
  }
}
