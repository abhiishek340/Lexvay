import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import auraInputtext from '@primeuix/themes/aura/inputtext';
import auraPassword from '@primeuix/themes/aura/password';
import { Button, ButtonDirective, ButtonLabel } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { BUSY, Countdown, busyRetryAfter } from '../../core/busy';
import { DEMO_ACCOUNTS, DemoAccount } from '../../core/demo';
import { ROLE_LABEL } from '../../core/labels';
import { MotionPreferences } from '../../core/motion';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { AuthFrame } from '../../ui/auth-frame';
import { StateMap } from '../../ui/state-map/state-map';
import { STORY_STEP_MS, STORY_STILL, storyAt } from './sign-in-story';

type Story = 'playing' | 'stopped' | 'off';
type Field = 'email' | 'password';

/**
 * Sign in (DESIGN.md "Screens"): the story map and the headline on the left, the form on the right. The story
 * plays until the person starts typing and never under reduced motion, where the map stands still. A session the
 * server ended arrives here with its reason (core/access.ts); a busy server holds the button for its Retry-After.
 */
@Component({
  selector: 'lx-login',
  imports: [FormsModule, Button, ButtonDirective, ButtonLabel, InputText, Message, Password, AuthFrame, StateMap],
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

  readonly story = signal<Story>(inject(MotionPreferences).reduced() ? 'off' : 'playing');
  private readonly elapsed = signal(0);
  readonly picture = computed(() => (this.story() === 'off' ? STORY_STILL : storyAt(this.elapsed())));
  private timer = 0;

  constructor() {
    registerComponentTokens({ inputtext: auraInputtext, message: lexvayMessage, password: auraPassword });
    if (this.story() === 'playing') {
      const started = performance.now();
      this.timer = window.setInterval(() => this.elapsed.set(performance.now() - started), STORY_STEP_MS);
    }
    inject(DestroyRef).onDestroy(() => clearInterval(this.timer));
  }

  /** Typing stops the story where it is: nothing moves while someone signs in. */
  stopStory(): void {
    if (this.story() === 'playing') {
      clearInterval(this.timer);
      this.story.set('stopped');
    }
  }

  /** A demo chip fills the email and moves on to the password (the fictional seed's shared password). */
  useAccount(account: DemoAccount): void {
    this.stopStory();
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
