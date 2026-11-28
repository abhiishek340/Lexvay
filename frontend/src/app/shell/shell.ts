import { Component, DestroyRef, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, ResolveEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { Tooltip } from 'primeng/tooltip';
import { filter, firstValueFrom } from 'rxjs';
import { ApiService } from '../core/api.service';
import { ROLE_LABEL } from '../core/labels';
import { NavLink, TAB_BAR_WIDTH, beforeSignIn, inLink, navLinks, screenOf, tabBarOverflow, tabSlots } from '../core/navigation';
import { ProgramContext } from '../core/program-context';
import { SessionService } from '../core/session.service';
import { CommandPalette } from '../ui/command-palette/command-palette';
import { paletteShortcut } from '../ui/command-palette/shortcut';
import { Mark } from '../ui/mark';
import { initials } from '../ui/initials';
import { AccountMenu } from './account-menu';
import { MoreMenu } from './more-menu';
import { ProgramSwitcher } from './program-switcher';
import { ShellToast } from './shell-toast';
import { WorkQueueMenu } from './work-queue-menu';

const RAIL_KEY = 'lexvay.rail.collapsed';

/**
 * The application frame (DESIGN.md v3 "Shell"): the 48 px top bar (mark and wordmark, the program switcher, the command
 * palette's search field, "Demo data", the work queue and the account button), the 220 px rail in two groups (56 px
 * collapsed or under 1200 px, a bottom tab bar under 640 px), and the main region. The popovers, the palette, the
 * program switcher and the toast are deferred chunks, so first paint carries only the bar, the rail and their icons.
 */
@Component({
  selector: 'lx-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIcon, Tooltip, Mark, AccountMenu, CommandPalette, MoreMenu, ProgramSwitcher, ShellToast, WorkQueueMenu],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
  host: { '(document:keydown)': 'onKey($event)' },
})
export class Shell {
  readonly session = inject(SessionService);
  readonly context = inject(ProgramContext);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly roleLabel = ROLE_LABEL;
  readonly shortcut = paletteShortcut(typeof navigator === 'undefined' ? '' : navigator.platform);

  /**
   * The URL being shown, set when a navigation has resolved (just before its screen is created), so the bar, the rail
   * and the screen appear in the same frame and nothing shifts; null before the first navigation.
   */
  private readonly url = signal<string | null>(null);
  readonly programId = signal<string | null>(null);
  /** Until the first navigation resolves, the frame stays invisible, so the bar never pushes a painted frame down. */
  readonly pending = computed(() => this.url() === null);
  /** The top bar and navigation appear once someone is signed in; sign-in and choosing a password stand alone. */
  readonly chrome = computed(() => this.session.authenticated() && this.url() !== null && !beforeSignIn(this.url()!));
  /** The rail is on every program screen and on the tenant-wide ones (the register, People), which need no program. */
  readonly railVisible = computed(() =>
    this.chrome() && (this.programId() !== null || this.links().some((link) => link.tenant && link.path.join('/') === this.path())));
  /** The screen segment kept when switching programs ("review" in /programs/p1/review?jurisdiction=CA). */
  readonly screen = computed(() => (this.url() ?? '').split(/[?#]/)[0].split('/')[3] ?? 'overview');

  /** The tenant-wide screens first, then those of the program in the URL (elsewhere, of the program last worked on). */
  readonly links = computed(() => navLinks(this.programId() ?? this.context.program()?.id ?? null, {
    waitingForReview: this.context.counts()?.waitingForReview ?? 0,
    openChanges: this.context.openChanges(),
    rulesWaiting: this.context.rulesWaiting(),
    openRequests: this.context.openRequests(),
  }, this.session.user()?.role ?? null));

  /** The rail in its two groups (DESIGN.md "Rail"): the tenant-wide screens, then the program's. */
  readonly groups = computed(() => [
    { name: 'Workspace', links: this.links().filter((link) => link.tenant) },
    { name: 'Program', links: this.links().filter((link) => !link.tenant) },
  ].filter((group): group is { name: string; links: NavLink[] } => group.links.length > 0));

  /** The window's width, for the tab bar (under 640 px) and the rail that shows only icons (under 1200 px). */
  private readonly width = signal(window.innerWidth);
  readonly tabBar = computed(() => this.width() < TAB_BAR_WIDTH);
  /** Icons are 16 px in the rail and 20 px in the bottom tab bar. */
  readonly iconSize = computed(() => (this.tabBar() ? '20' : '16'));
  /** Whether the rail shows icons alone, so each link's name moves into its tooltip (the tab bar has none: touch has no hover). */
  readonly collapsedView = computed(() => !this.tabBar() && (this.collapsed() || this.width() < 1200));
  /** How many tabs the bottom bar has room for, and the screens that go into "More". */
  private readonly slots = computed(() => tabSlots(this.width()));
  readonly overflow = computed(() => tabBarOverflow(this.links(), this.slots()));
  readonly overflowIds = computed(() => new Set(this.overflow().map((link) => link.id)));
  /** The URL path, for marking "More" when the screen shown is one of its own. */
  readonly path = computed(() => (this.url() ?? '').split(/[?#]/)[0]);
  readonly moreCurrent = computed(() => this.overflow().find((link) => inLink(this.path(), link)) ?? null);
  readonly moreOpen = signal(false);

  readonly initials = computed(() => initials(this.session.user()?.displayName ?? ''));
  readonly collapsed = signal(readCollapsed());
  readonly paletteOpen = signal(false);
  readonly queueCount = signal(0);
  readonly queueOpen = signal(false);
  readonly accountOpen = signal(false);
  readonly demoNotice = computed(() =>
    `${this.session.tenantName()}, its people, courses, rule text and findings are invented for this prototype. Nothing here is licensing guidance.`);

  readonly accountMenu = viewChild<AccountMenu>('accountMenu');
  readonly queueMenu = viewChild<WorkQueueMenu>('queueMenu');
  readonly moreMenu = viewChild<MoreMenu>('moreMenu');

  constructor() {
    let previousScreen: string | null = null;
    const resolved = this.router.events.pipe(filter((e): e is ResolveEnd => e instanceof ResolveEnd)).subscribe((e) => {
      this.url.set(e.urlAfterRedirects);
      const programId = findParam(e.state.root, 'programId');
      this.programId.set(programId);
      if (programId) {
        this.context.ensure(programId);
      }
    });
    const sub = this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)).subscribe((e) => {
      const screen = screenOf(e.urlAfterRedirects);
      if (this.chrome()) {
        this.refreshQueueCount();
      }
      // Moving to another screen puts focus on its heading. The first page load keeps the browser's start, and
      // a change within a screen (a jurisdiction tab, a rule version in the list) keeps focus where the person is working.
      if (previousScreen !== null && screen !== previousScreen) {
        focusHeadingWhenRendered();
      }
      previousScreen = screen;
    });
    const onResize = (): void => this.width.set(window.innerWidth);
    window.addEventListener('resize', onResize);
    inject(DestroyRef).onDestroy(() => { resolved.unsubscribe(); sub.unsubscribe(); window.removeEventListener('resize', onResize); });
  }

  /** Ctrl K or Cmd K opens the command palette from anywhere, including text fields. */
  onKey(event: KeyboardEvent): void {
    if (this.chrome() && (event.ctrlKey || event.metaKey) && !event.altKey && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.paletteOpen.set(true);
    }
  }

  toggleRail(): void {
    const next = !this.collapsed();
    this.collapsed.set(next);
    try { localStorage.setItem(RAIL_KEY, String(next)); } catch { /* storage may be unavailable; the choice then lasts for this page */ }
  }

  openQueue(event: Event): void {
    this.queueMenu()?.toggle(event);
  }

  openMore(event: Event): void {
    this.moreMenu()?.toggle(event);
  }

  openAccount(event: Event): void {
    this.accountMenu()?.toggle(event);
  }

  private async refreshQueueCount(): Promise<void> {
    try {
      this.queueCount.set((await firstValueFrom(this.api.workQueueCount())).total);
    } catch {
      // The dot is a convenience; the popover reports errors when it is opened.
    }
  }
}

function readCollapsed(): boolean {
  try { return localStorage.getItem(RAIL_KEY) === 'true'; } catch { return false; }
}

function findParam(route: ActivatedRouteSnapshot, name: string): string | null {
  for (let r: ActivatedRouteSnapshot | null = route; r; r = r.firstChild) {
    const value = r.paramMap.get(name);
    if (value) { return value; }
  }
  return null;
}

/**
 * Screens render their heading once their data arrives; wait for it briefly, then focus it. If the person moves focus
 * while the screen loads (opens a menu, starts typing), the heading leaves it where they put it.
 */
export function focusHeadingWhenRendered(): void {
  const started = performance.now();
  const focusAtStart = document.activeElement;
  const attempt = (): void => {
    const focus = document.activeElement;
    if (focus !== focusAtStart && focus !== document.body && focus !== null) {
      return;
    }
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
