import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { SessionView, UserView } from './models';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly api = inject(ApiService);
  private readonly state = signal<SessionView | null>(null);
  /** Why the last session ended without the person signing out ("Your access changed. Sign in again."); sign-in shows it. */
  readonly notice = signal<string | null>(null);

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
  /** Creating a program and adding its courses, documents and facts (POST /programs and the endpoints under it). */
  readonly canSetUp = computed(() => ['CONTRIBUTOR', 'ADMIN'].includes(this.user()?.role ?? ''));

  /**
   * A session that no longer matches its account is answered 401 once and is gone after that answer (D-374), so the
   * second call reports the signed-out state.
   */
  async load(): Promise<SessionView> {
    const session = await firstValueFrom(this.api.session()).catch((e: unknown) => {
      if (e instanceof HttpErrorResponse && e.status === 401) { return firstValueFrom(this.api.session()); }
      throw e;
    });
    this.state.set(session);
    return session;
  }

  /** The server ended the session: forget it here too, so the next screen asks again, and keep the reason for sign-in. */
  end(notice: string): void {
    this.state.set(null);
    this.notice.set(notice);
  }

  /** Loads the session first so the CSRF cookie exists before the login POST (the header is only added when the cookie is present). */
  async login(email: string, password: string): Promise<void> {
    if (!this.loaded()) {
      await this.load();
    }
    await firstValueFrom(this.api.login(email, password));
    this.notice.set(null);
    await this.load();
  }

  async logout(): Promise<void> {
    await firstValueFrom(this.api.logout());
    await this.load();
  }
}
