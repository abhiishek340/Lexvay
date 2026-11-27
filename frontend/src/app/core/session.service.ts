import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { SessionView, UserView } from './models';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly api = inject(ApiService);
  private readonly state = signal<SessionView | null>(null);

  readonly loaded = computed(() => this.state() !== null);
  readonly user = computed<UserView | null>(() => this.state()?.user ?? null);
  readonly authenticated = computed(() => this.state()?.authenticated ?? false);
  readonly demo = computed(() => this.state()?.demo ?? false);
  readonly tenantName = computed(() => this.state()?.tenantName ?? '');
  readonly isSigner = computed(() => this.user()?.role === 'SIGNER');
  /** Administrators also download the audit trail as a file (GET /audit.csv). */
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');
  readonly canReview = computed(() => ['REVIEWER', 'SIGNER', 'ADMIN'].includes(this.user()?.role ?? ''));
  /** Entering a rule version (POST /rule-versions) is for contributors and administrators. */
  readonly canEnterRules = computed(() => ['CONTRIBUTOR', 'ADMIN'].includes(this.user()?.role ?? ''));

  async load(): Promise<SessionView> {
    const session = await firstValueFrom(this.api.session());
    this.state.set(session);
    return session;
  }

  /** Loads the session first so the CSRF cookie exists before the login POST (the header is only added when the cookie is present). */
  async login(email: string, password: string): Promise<void> {
    if (!this.loaded()) {
      await this.load();
    }
    await firstValueFrom(this.api.login(email, password));
    await this.load();
  }

  async logout(): Promise<void> {
    await firstValueFrom(this.api.logout());
    await this.load();
  }
}
