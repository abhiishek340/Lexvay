import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import auraInputtext from '@primeuix/themes/aura/inputtext';
import auraPassword from '@primeuix/themes/aura/password';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { BUSY, Countdown, busyRetryAfter } from '../../core/busy';
import { DEMO_ACCOUNTS, DemoAccount } from '../../core/demo';
import { ROLE_LABEL } from '../../core/labels';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { AuthFrame } from '../../ui/auth-frame';

type Field = 'email' | 'password';

/**
 * Sign in (DESIGN.md v3 "Sign in"): one centred column with the form and, for the demo tenant, its fictional accounts.
 * A session the server ended arrives here with its reason (core/access.ts); a busy server holds the button for its
 * Retry-After.
 */
@Component({
  selector: 'lx-login',
  imports: [FormsModule, Button, InputText, Message, Password, AuthFrame],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  readonly session = inject(SessionService);
  private readonly router = inject(Router);
  email = '';
  password = '';
  readonly error = signal<{ field: Field; text: string } | null>(null);
  readonly busy = signal(false);
  readonly wait = new Countdown(inject(DestroyRef));
  readonly demoAccounts = DEMO_ACCOUNTS;
  readonly roleLabel = ROLE_LABEL;

  constructor() {
    registerComponentTokens({ inputtext: auraInputtext, message: lexvayMessage, password: auraPassword });
  }

  /** A demo account fills the email and moves on to the password (the fictional seed's shared password). */
  useAccount(account: DemoAccount): void {
    this.email = account.email;
    this.error.set(null);
    document.getElementById('password')?.focus();
  }

  async submit(): Promise<void> {
    const email = this.email.trim();
    if (!email || !this.password) {
      this.error.set({ field: email ? 'password' : 'email', text: 'Enter your email and password.' });
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    try {
      await this.session.login(email, this.password);
      await this.router.navigate(['/']);
    } catch (e: unknown) {
      const seconds = busyRetryAfter(e);
      if (seconds !== null) { this.wait.start(seconds); }
      this.error.set({ field: 'password', text: seconds !== null ? BUSY : 'Email or password is incorrect.' });
    } finally {
      this.busy.set(false);
    }
  }
}
