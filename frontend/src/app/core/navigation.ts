import type { IconName } from '../theme/icons';
import type { Role } from './models';

/** A screen of the product as the rail, the bottom tab bar and the command palette show it. */
export interface NavLink {
  readonly id: string;
  readonly path: string[];
  /** The full name: the link's accessible name, the tooltip and the command palette's entry. */
  readonly label: string;
  /** What the rail prints; shorter than the label only where the full name would not fit (word budget). */
  readonly short: string;
  readonly icon: IconName;
  /** Tenant-wide screens sit above the divider, program screens below it. */
  readonly tenant: boolean;
  readonly badge: number;
  readonly badgeText: string;
  /**
   * How much the screen is used, higher first. When the bottom tab bar (under 640 px) cannot fit every screen, the
   * lowest move into its "More" menu (tabBarOverflow); a new screen needs only its value here.
   */
  readonly priority: number;
}

export interface NavCounts { readonly waitingForReview: number; readonly openRequests: number; readonly openChanges: number; readonly rulesWaiting: number }

const plural = (n: number, one: string, many: string): string => `${n} ${n === 1 ? one : many}`;

/**
 * The tenant-wide screens first (the register; People for administrators, who alone manage accounts), then the
 * screens of the given program (none before a program was opened). The only list of screens: the rail and the
 * command palette both read it.
 */
export function navLinks(programId: string | null, counts: NavCounts, role: Role | null): NavLink[] {
  const tenant = (id: string, label: string, icon: IconName, priority: number): NavLink =>
    ({ id, path: [`/${id}`], label, short: label, icon, tenant: true, badge: 0, badgeText: '', priority });
  const tenantLinks = [
    tenant('register', 'Determination register', 'phosphorBookBookmark', 60),
    ...(role === 'ADMIN' ? [tenant('people', 'People', 'phosphorUsers', 20)] : []),
  ];
  if (!programId) {
    return tenantLinks;
  }
  const screen = (id: string, label: string, icon: IconName, priority: number, badge = 0, badgeText = '', short = label): NavLink =>
    ({ id, path: ['/programs', programId, id], label, short, icon, tenant: false, badge, badgeText, priority });
  const { waitingForReview: waiting, openRequests: requests, openChanges: open, rulesWaiting: rules } = counts;
  return [
    ...tenantLinks,
    screen('overview', 'Program overview', 'phosphorSquaresFour', 90),
    screen('review', 'Review', 'phosphorListChecks', 85, waiting, `${plural(waiting, 'requirement', 'requirements')} waiting for review`),
    screen('coverage', 'Coverage', 'phosphorGridFour', 80),
    screen('requests', 'Evidence requests', 'phosphorTray', 75, requests, `${plural(requests, 'open evidence request', 'open evidence requests')}`, 'Requests'),
    screen('changes', 'Changes', 'phosphorGitDiff', 70, open, `${plural(open, 'open change', 'open changes')}`),
    screen('rules', 'Rules', 'phosphorScales', 40, rules, `${plural(rules, 'rule version', 'rule versions')} waiting for approval`),
    screen('report', 'Evidence report', 'phosphorFileText', 30),
    screen('audit', 'Audit trail', 'phosphorClockCounterClockwise', 10),
  ];
}

/** The bottom tab bar's geometry (shell.css, under 640 px): 4 px of padding on each side, at least 40 px a tab. */
const TAB_BAR_PADDING = 8;
const TAB_MIN_WIDTH = 40;

/** How many tabs fit a window this wide, the "More" tab included. */
export function tabSlots(windowWidth: number): number {
  return Math.max(2, Math.floor((windowWidth - TAB_BAR_PADDING) / TAB_MIN_WIDTH));
}

/**
 * The screens that go into the bottom tab bar's "More" menu, in rail order: none when every screen fits the slots;
 * otherwise all but the `slots - 1` most used (by priority; on a tie, the earlier in the rail), whose place the "More"
 * tab takes.
 */
export function tabBarOverflow(links: readonly NavLink[], slots: number): NavLink[] {
  if (links.length <= slots) { return []; }
  const kept = new Set([...links].sort((a, b) => b.priority - a.priority).slice(0, Math.max(slots - 1, 0)).map((link) => link.id));
  return links.filter((link) => !kept.has(link.id));
}

/** True when the URL's path is the link's screen or inside it ("/programs/p1/rules/rv7" is in Rules). */
export function inLink(url: string, link: NavLink): boolean {
  const path = url.split(/[?#]/)[0];
  const base = link.path.join('/');
  return path === base || path.startsWith(`${base}/`);
}

/** Sign-in and choosing a password: pages that stand alone, without the top bar and the rail, signed in or not. */
export function beforeSignIn(url: string): boolean {
  return /^\/(login|setup)(?=[/?#]|$)/.test(url);
}

/** "/programs/p1/rules/rv7?x=1" is the screen "/programs/p1/rules": the path up to the screen's own segment. */
export function screenOf(url: string): string {
  return url.split(/[?#]/)[0].split('/').slice(0, 4).join('/');
}

/** A navigation that stays on one screen (a tab, a filter, a version in a list) is not a route change. */
export function sameScreen(from: string, to: string): boolean {
  return screenOf(from) === screenOf(to);
}
