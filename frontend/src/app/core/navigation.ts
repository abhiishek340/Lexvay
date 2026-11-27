import type { IconName } from '../theme/icons';

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
}

export interface NavCounts { readonly waitingForReview: number; readonly openRequests: number; readonly openChanges: number; readonly rulesWaiting: number }

const plural = (n: number, one: string, many: string): string => `${n} ${n === 1 ? one : many}`;

/**
 * The tenant-wide register first, then the screens of the given program (none before a program was opened). The
 * only list of screens: the rail and the command palette both read it.
 */
export function navLinks(programId: string | null, counts: NavCounts): NavLink[] {
  const register: NavLink = { id: 'register', path: ['/register'], label: 'Determination register', short: 'Determination register', icon: 'phosphorBookBookmark', tenant: true, badge: 0, badgeText: '' };
  if (!programId) {
    return [register];
  }
  const screen = (id: string, label: string, icon: IconName, badge = 0, badgeText = '', short = label): NavLink =>
    ({ id, path: ['/programs', programId, id], label, short, icon, tenant: false, badge, badgeText });
  const { waitingForReview: waiting, openRequests: requests, openChanges: open, rulesWaiting: rules } = counts;
  return [
    register,
    screen('overview', 'Program overview', 'phosphorSquaresFour'),
    screen('review', 'Review', 'phosphorListChecks', waiting, `${plural(waiting, 'requirement', 'requirements')} waiting for review`),
    screen('requests', 'Evidence requests', 'phosphorTray', requests, `${plural(requests, 'open evidence request', 'open evidence requests')}`, 'Requests'),
    screen('changes', 'Changes', 'phosphorGitDiff', open, `${plural(open, 'open change', 'open changes')}`),
    screen('rules', 'Rules', 'phosphorScales', rules, `${plural(rules, 'rule version', 'rule versions')} waiting for approval`),
    screen('report', 'Evidence report', 'phosphorFileText'),
    screen('audit', 'Audit trail', 'phosphorClockCounterClockwise'),
  ];
}

/** "/programs/p1/rules/rv7?x=1" is the screen "/programs/p1/rules": the path up to the screen's own segment. */
export function screenOf(url: string): string {
  return url.split(/[?#]/)[0].split('/').slice(0, 4).join('/');
}

/** A navigation that stays on one screen (a tab, a filter, a version in a list) is not a route change. */
export function sameScreen(from: string, to: string): boolean {
  return screenOf(from) === screenOf(to);
}
