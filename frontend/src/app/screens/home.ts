import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../core/api.service';
import { licenseLabel } from '../core/labels';
import { Program } from '../core/models';
import { problemDetail } from '../core/problem';
import { SessionService } from '../core/session.service';
import { registerComponentTokens } from '../theme/component-tokens';
import { lexvayMessage } from '../theme/message-tokens';
import { lexvaySkeleton } from '../theme/skeleton-tokens';

/** Lands on the only program, or lists them. */
@Component({
  selector: 'lx-home',
  imports: [RouterLink, NgIcon, ButtonDirective, ButtonLabel, Message, Skeleton],
  template: `
    <div class="lx-page">
      <header class="lx-page-header">
        <div><h1>Programs</h1></div>
        @if (session.canSetUp()) {
          <a pButton routerLink="/programs/new"><span pButtonLabel>New program</span></a>
        }
      </header>
      @if (error(); as e) {
        <p-message severity="error">{{ e }}</p-message>
      } @else if (programs(); as list) {
        @if (list.length === 0) {
          <p class="lx-muted">No programs yet.</p>
        } @else {
          <ul class="lx-programs">
            @for (p of list; track p.id) {
              <li>
                <a [routerLink]="['/programs', p.id, 'overview']">{{ p.name }}</a>
                <span class="lx-muted">{{ license(p) }} · catalog year {{ p.catalogYear }}</span>
                <ng-icon name="phosphorArrowRight" size="16" aria-hidden="true" />
              </li>
            }
          </ul>
        }
      } @else {
        <div class="lx-skeleton" role="status" aria-busy="true" aria-label="Loading programs"><p-skeleton aria-hidden="true" width="24rem" height="2.5rem" /><p-skeleton aria-hidden="true" width="20rem" height="2.5rem" /></div>
      }
    </div>
  `,
  styles: `
    .lx-programs { list-style: none; padding: 0; margin: 0; max-width: 720px; border-top: 1px solid var(--p-lx-line); }
    .lx-programs li { display: flex; gap: 12px; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--p-lx-line); }
    .lx-programs a { font-weight: 500; }
    .lx-programs span { flex: 1; }
    .lx-skeleton { display: flex; flex-direction: column; gap: 8px; }
  `,
})
export class Home {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly session = inject(SessionService);
  readonly programs = signal<Program[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly license = licenseLabel;

  constructor() {
    registerComponentTokens({ message: lexvayMessage, skeleton: lexvaySkeleton });
    firstValueFrom(this.api.programs()).then((list) => {
      if (list.length === 1) {
        this.router.navigate(['/programs', list[0].id, 'overview'], { replaceUrl: true });
      } else {
        this.programs.set(list);
      }
    }).catch((e: unknown) => this.error.set(problemDetail(e, 'The programs could not be loaded.')));
  }
}
