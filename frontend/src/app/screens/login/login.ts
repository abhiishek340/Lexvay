import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { SessionService } from '../../core/session.service';

@Component({
  selector: 'lx-login',
  imports: [FormsModule, Button, InputText, Message, Password],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  email = '';
  password = '';
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);

  async submit(): Promise<void> {
    if (!this.email.trim() || !this.password) {
      this.error.set('Enter your email and password.');
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    try {
      await this.session.login(this.email.trim(), this.password);
      await this.router.navigate(['/']);
    } catch {
      this.error.set('Email or password is incorrect.');
    } finally {
      this.busy.set(false);
    }
  }
}
