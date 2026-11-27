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

interface NavLink { id: string; path: string[]; label: string; icon: IconName; badge: number; badgeText: string }

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

  /**
   * The tenant-wide register first, then the screens of the program in the URL (on the register, of the program
   * last worked on; none before a program was opened).
   */
  readonly links = computed<NavLink[]>(() => {
    const register: NavLink = { id: 'register', path: ['/register'], label: 'Determination register', icon: 'phosphorBookBookmark', badge: 0, badgeText: '' };
    const programId = this.programId() ?? this.context.program()?.id;
    if (!programId) {
      return [register];
    }
    const waiting = this.context.counts()?.waitingForReview ?? 0;
    const open = this.context.openChanges();
    const rules = this.context.rulesWaiting();
    const screen = (id: string, label: string, icon: IconName, badge = 0, badgeText = ''): NavLink =>
      ({ id, path: ['/programs', programId, id], label, icon, badge, badgeText });
    return [
      register,
      screen('overview', 'Program overview', 'phosphorSquaresFour'),
      screen('review', 'Review', 'phosphorListChecks', waiting, `${waiting} requirement${waiting === 1 ? '' : 's'} waiting for review`),
      screen('changes', 'Changes', 'phosphorGitDiff', open, `${open} open change${open === 1 ? '' : 's'}`),
      screen('rules', 'Rules', 'phosphorScales', rules, `${rules} rule version${rules === 1 ? '' : 's'} waiting for approval`),
      screen('report', 'Evidence report', 'phosphorFileText'),
      screen('audit', 'Audit trail', 'phosphorClockCounterClockwise'),
    ];
  });

  readonly menuItems: MenuItem[] = [{ label: 'Sign out', id: 'sign-out', command: () => this.signOut() }];
  /** The menu, the toast and the program switcher are deferred chunks; the menu is ready once the page is idle. */
  private readonly menu = viewChild<Menu>('accountMenu');

  constructor() {
    let previousScreen: string | null = null;
    const sub = this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)).subscribe((e) => {
      this.url.set(e.urlAfterRedirects);
      const screen = screenOf(e.urlAfterRedirects);
      const programId = findParam(this.router.routerState.snapshot.root, 'programId');
      this.programId.set(programId);
      if (programId) {
        this.context.ensure(programId);
      }
      // Moving to another screen puts focus on its heading. The first page load keeps the browser's start, and
      // a change within a screen (a jurisdiction tab, a rule version in the list) keeps focus where the person is working.
      if (previousScreen !== null && screen !== previousScreen) {
        focusHeadingWhenRendered();
      }
      previousScreen = screen;
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

/** "/programs/p1/rules/rv7?x=1" is the screen "/programs/p1/rules": the path up to the screen's own segment. */
function screenOf(url: string): string {
  return url.split(/[?#]/)[0].split('/').slice(0, 4).join('/');
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
