import { ChangeEvent, Determination } from '../../core/models';
import type { IconName } from '../../theme/icons';
import { CHANGE_KIND } from './change-kind';
import { rerunBlocker } from './change-rules';

/** One card of the Changes timeline: what changed, from what to what, where it stands and the one action it offers. */
export interface ChangeCard {
  id: string;
  event: ChangeEvent;
  icon: IconName;
  /** "Colorado rules, revision 2", "COUN 625, new catalog year". */
  title: string;
  /** The two pills: the part of each version name that differs ("2026" and "2026 revision 2"). */
  from: string;
  to: string;
  /** The whole version names, for the details. */
  fromFull: string;
  toFull: string;
  versionNoun: string;
  open: boolean;
  /** "Re-run assessment" or "Re-run matching". */
  action: string;
  /** Why the action cannot run yet; null when it can (or the change is handled). */
  blocker: string | null;
  /** What the change reopened, in words: "California", "California, 1 in another program", "No signed determination". */
  affects: string;
  createdAt: string;
}

const LEADING_PUNCTUATION = /^[-–—:,·|]+$/;

/**
 * The part of two version names a reader needs: the words after their shared beginning, keeping at least the last
 * shared word when one name is the other plus more. "Demo rule set - Colorado (fictional) 2026" and "... 2026
 * revision 2" become "2026" and "2026 revision 2"; `added` is what only the new name has ("revision 2").
 */
export function versionPills(oldName: string, newName: string): { from: string; to: string; added: string } {
  const a = oldName.trim().split(/\s+/);
  const b = newName.trim().split(/\s+/);
  let shared = 0;
  while (shared < a.length && shared < b.length && a[shared] === b[shared]) { shared++; }
  if (shared === a.length && shared === b.length) { return { from: oldName.trim(), to: newName.trim(), added: '' }; }
  const start = Math.min(shared, a.length - 1, b.length - 1);
  const words = (list: string[], from: number): string => {
    const rest = list.slice(from);
    while (rest.length > 1 && LEADING_PUNCTUATION.test(rest[0])) { rest.shift(); }
    return rest.join(' ');
  };
  return { from: words(a, start), to: words(b, start), added: words(b, shared) };
}

/**
 * The signed determinations a change reopened, by the state they are for when they belong to this program (the
 * determinations the screen has), counted otherwise: the change list is tenant-wide.
 */
export function affects(ids: string[], jurisdictionName: (code: string) => string, determinations: Determination[]): string {
  if (ids.length === 0) { return 'No signed determination'; }
  const program = new Map(determinations.map((d) => [d.id, d.jurisdiction]));
  const here = ids.filter((id) => program.has(id));
  const names = [...new Set(here.map((id) => jurisdictionName(program.get(id)!)))].sort();
  const elsewhere = ids.length - here.length;
  const other = elsewhere > 0 ? [`${elsewhere} in ${elsewhere === 1 ? 'another program' : 'other programs'}`] : [];
  return [...names, ...other].join(', ');
}

/** The card for one change event. `jurisdictionName` turns "CO" into "Colorado". */
export function changeCard(e: ChangeEvent, jurisdictionName: (code: string) => string, determinations: Determination[]): ChangeCard {
  const kind = CHANGE_KIND[e.kind];
  const base = {
    id: e.id, event: e, icon: kind.icon, open: e.status === 'OPEN', action: kind.action, blocker: rerunBlocker(e),
    affects: affects(e.reopenedDeterminationIds, jurisdictionName, determinations), createdAt: e.createdAt,
  };
  if (e.kind === 'RULE_VERSION') {
    const fromFull = e.oldLabel ?? 'Previous rule version';
    const toFull = e.newLabel ?? 'New rule version';
    const pills = versionPills(fromFull, toFull);
    const where = e.jurisdiction ? jurisdictionName(e.jurisdiction) : 'State';
    return { ...base, title: `${where} rules, ${pills.added || 'new version'}`, from: pills.from, to: pills.to, fromFull, toFull, versionNoun: 'Rule version' };
  }
  const fromFull = e.oldCatalogYear ?? 'Earlier catalog year';
  const toFull = e.newCatalogYear ?? 'New catalog year';
  return { ...base, title: `${e.courseCode ?? 'Course'}, new catalog year`, from: fromFull, to: toFull, fromFull, toFull, versionNoun: 'Catalog year' };
}

/** Newest first: the timeline reads from the latest change down. */
export function changeCards(events: ChangeEvent[], jurisdictionName: (code: string) => string, determinations: Determination[]): ChangeCard[] {
  return [...events].sort((x, y) => y.createdAt.localeCompare(x.createdAt)).map((e) => changeCard(e, jurisdictionName, determinations));
}
