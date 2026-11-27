import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../core/api.service';
import { licenseLabel } from '../core/labels';
import { Program } from '../core/models';
import { problemDetail } from '../core/problem';

/** Lands on the only program, or lists them. */
@Component({
  selector: 'lx-home',
  imports: [RouterLink, NgIcon, Message, Skeleton],
  template: `
    <div class="lx-page">
      <header class="lx-page-header"><div><h1>Programs</h1><p>Choose the program to work on.</p></div></header>
      @if (error(); as e) {
        <p-message severity="error">{{ e }}</p-message>
      } @else if (programs(); as list) {
        @if (list.length === 0) {
          <p class="lx-muted">No programs yet. A program appears here once a contributor creates it.</p>
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
        <div class="lx-skeleton" aria-busy="true" aria-label="Loading programs"><p-skeleton width="24rem" height="2.5rem" /><p-skeleton width="20rem" height="2.5rem" /></div>
      }
    </div>
  `,
  styles: `
    .lx-programs { list-style: none; padding: 0; margin: 0; max-width: 720px; border-top: 1px solid var(--p-lx-hairline); }
    .lx-programs li { display: flex; gap: 12px; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--p-lx-hairline); }
    .lx-programs a { font-weight: 500; }
    .lx-programs span { flex: 1; }
    .lx-skeleton { display: flex; flex-direction: column; gap: 8px; }
  `,
})
export class Home {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly programs = signal<Program[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly license = licenseLabel;

  constructor() {
    firstValueFrom(this.api.programs()).then((list) => {
      if (list.length === 1) {
        this.router.navigate(['/programs', list[0].id, 'overview'], { replaceUrl: true });
      } else {
        this.programs.set(list);
      }
    }).catch((e: unknown) => this.error.set(problemDetail(e, 'The programs could not be loaded.')));
  }
}
