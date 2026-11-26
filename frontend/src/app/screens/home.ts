import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../core/api.service';
import { Program } from '../core/models';

/** Lands on the only program, or lists them. */
@Component({
  selector: 'lx-home',
  imports: [RouterLink],
  template: `
    <h1>Programs</h1>
    @if (programs(); as list) {
      @if (list.length === 0) { <p>No programs yet.</p> }
      <ul>
        @for (p of list; track p.id) { <li><a [routerLink]="['/programs', p.id, 'overview']">{{ p.name }}</a></li> }
      </ul>
    }
  `,
})
export class Home {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly programs = signal<Program[] | null>(null);

  constructor() {
    firstValueFrom(this.api.programs()).then((list) => {
      this.programs.set(list);
      if (list.length === 1) { this.router.navigate(['/programs', list[0].id, 'overview']); }
    });
  }
}
