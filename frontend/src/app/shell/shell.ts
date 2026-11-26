import { LowerCasePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionService } from '../core/session.service';
import { ThemeService } from '../core/theme.service';

@Component({
  selector: 'lx-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LowerCasePipe],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  readonly session = inject(SessionService);
  readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly programId = computed(() => this.route.snapshot.firstChild?.paramMap.get('programId') ?? null);

  links(programId: string): { label: string; path: string[] }[] {
    return [
      { label: 'Program overview', path: ['/programs', programId, 'overview'] },
      { label: 'Review', path: ['/programs', programId, 'review'] },
      { label: 'Changes', path: ['/programs', programId, 'changes'] },
      { label: 'Evidence report', path: ['/programs', programId, 'report'] },
      { label: 'Audit trail', path: ['/programs', programId, 'audit'] },
    ];
  }

  currentProgramId(): string | null {
    let r: ActivatedRoute | null = this.route;
    while (r) {
      const id = r.snapshot.paramMap.get('programId');
      if (id) { return id; }
      r = r.firstChild;
    }
    return null;
  }

  async signOut(): Promise<void> {
    await this.session.logout();
    await this.router.navigate(['/login']);
  }
}
