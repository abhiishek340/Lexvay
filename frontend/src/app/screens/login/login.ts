import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SessionService } from '../../core/session.service';

@Component({
  selector: 'lx-login',
  imports: [FormsModule],
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
