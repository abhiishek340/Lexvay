import { Component, DestroyRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { MenuItem } from 'primeng/api';
import { Badge } from 'primeng/badge';
import { ButtonDirective } from 'primeng/button';
import { Menu } from 'primeng/menu';
import { Select } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { Toast } from 'primeng/toast';
import { filter } from 'rxjs';
import { ROLE_LABEL } from '../core/labels';
import { ProgramContext } from '../core/program-context';
import { SessionService } from '../core/session.service';
import { Theme, ThemeService } from '../core/theme.service';
import { IconName } from '../theme/icons';

interface NavLink { id: string; programId: string; label: string; icon: IconName; badge: number; badgeText: string }

@Component({
  selector: 'lx-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, NgIcon, Badge, ButtonDirective, Menu, Select, SelectButton, Toast],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  readonly session = inject(SessionService);
  readonly theme = inject(ThemeService);
  readonly context = inject(ProgramContext);
  private readonly router = inject(Router);

  readonly themes: { value: Theme; label: string; icon: IconName }[] = [
    { value: 'system', label: 'System', icon: 'phosphorMonitor' },
    { value: 'light', label: 'Light', icon: 'phosphorSun' },
    { value: 'dark', label: 'Dark', icon: 'phosphorMoon' },
  ];
  readonly roleLabel = ROLE_LABEL;

  private readonly url = signal(this.router.url);
  readonly programId = signal<string | null>(null);
  /** The top bar and navigation appear once someone is signed in; the sign-in page stands alone. */
  readonly chrome = computed(() => this.session.authenticated() && !this.url().startsWith('/login'));

  /** The rail is on every program screen and on the tenant-wide register, which needs no program. */
  readonly railVisible = computed(() => this.chrome() && (this.programId() !== null || this.url().split(/[?#]/)[0] === '/register'));

  /** The program screens of the program in the URL, or on the register those of the program last worked on. */
  readonly links = computed<NavLink[]>(() => {
    const programId = this.programId() ?? this.context.program()?.id;
    if (!programId) {
      return [];
    }
    const waiting = this.context.counts()?.waitingForReview ?? 0;
    const open = this.context.openChanges();
    return [
      { id: 'overview', programId, label: 'Program overview', icon: 'phosphorSquaresFour', badge: 0, badgeText: '' },
      { id: 'review', programId, label: 'Review', icon: 'phosphorListChecks', badge: waiting, badgeText: `${waiting} requirement${waiting === 1 ? '' : 's'} waiting for review` },
      { id: 'changes', programId, label: 'Changes', icon: 'phosphorGitDiff', badge: open, badgeText: `${open} open change${open === 1 ? '' : 's'}` },
      { id: 'report', programId, label: 'Evidence report', icon: 'phosphorFileText', badge: 0, badgeText: '' },
      { id: 'audit', programId, label: 'Audit trail', icon: 'phosphorClockCounterClockwise', badge: 0, badgeText: '' },
    ];
  });

  readonly menuItems: MenuItem[] = [{ label: 'Sign out', id: 'sign-out', command: () => this.signOut() }];
  /** The menu, the toast and the program switcher are deferred chunks; the menu is ready once the page is idle. */
  private readonly menu = viewChild<Menu>('accountMenu');

  constructor() {
    let previousPath: string | null = null;
    const sub = this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)).subscribe((e) => {
      this.url.set(e.urlAfterRedirects);
      const path = e.urlAfterRedirects.split(/[?#]/)[0];
      const programId = findParam(this.router.routerState.snapshot.root, 'programId');
      this.programId.set(programId);
      if (programId) {
        this.context.ensure(programId);
      }
      // Moving to another screen puts focus on its heading. The first page load keeps the browser's start, and
      // a query-only change (a jurisdiction tab) keeps focus where the person is working.
      if (previousPath !== null && path !== previousPath) {
        focusHeadingWhenRendered();
      }
      previousPath = path;
    });
    inject(DestroyRef).onDestroy(() => sub.unsubscribe());
  }

  toggleMenu(event: Event): void {
    this.menu()?.toggle(event);
  }

  switchProgram(programId: string): void {
    const screen = this.url().split('?')[0].split('/')[3] ?? 'overview';
    this.router.navigate(['/programs', programId, screen]);
  }

  async signOut(): Promise<void> {
    await this.session.logout();
    this.context.clear();
    await this.router.navigate(['/login']);
  }
}

function findParam(route: ActivatedRouteSnapshot, name: string): string | null {
  for (let r: ActivatedRouteSnapshot | null = route; r; r = r.firstChild) {
    const value = r.paramMap.get(name);
    if (value) { return value; }
  }
  return null;
}

/** Screens render their heading once their data arrives; wait for it briefly, then focus it. */
function focusHeadingWhenRendered(): void {
  const started = performance.now();
  const attempt = (): void => {
    const heading = document.querySelector<HTMLElement>('main h1');
    if (heading) {
      heading.setAttribute('tabindex', '-1');
      heading.focus({ preventScroll: false });
    } else if (performance.now() - started < 3000) {
      requestAnimationFrame(attempt);
    }
  };
  requestAnimationFrame(attempt);
}
