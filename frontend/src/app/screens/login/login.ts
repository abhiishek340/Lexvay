import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { MotionPreferences } from '../../core/motion';
import { SessionService } from '../../core/session.service';
import { Mark } from '../../ui/mark';
import { StateMap } from '../../ui/state-map/state-map';
import { STORY_STEP_MS, STORY_STILL, storyAt } from './sign-in-story';

type Story = 'playing' | 'stopped' | 'off';
type Field = 'email' | 'password';

/**
 * Sign in (DESIGN.md "Screens"): the story map and the headline on the left, the form on the right. The story
 * plays until the person starts typing and never under reduced motion, where the map stands still.
 */
@Component({
  selector: 'lx-login',
  imports: [FormsModule, Button, InputText, Message, Password, Mark, StateMap],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  email = '';
  password = '';
  readonly error = signal<{ field: Field; text: string } | null>(null);
  readonly busy = signal(false);

  readonly story = signal<Story>(inject(MotionPreferences).reduced() ? 'off' : 'playing');
  private readonly elapsed = signal(0);
  readonly picture = computed(() => (this.story() === 'off' ? STORY_STILL : storyAt(this.elapsed())));
  private timer = 0;

  constructor() {
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
    } catch {
      this.error.set({ field: 'password', text: 'Email or password is incorrect.' });
    } finally {
      this.busy.set(false);
    }
  }
}
