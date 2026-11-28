import { ChangeEvent, Determination } from '../../core/models';
import { CHANGE_KIND } from './change-kind';
import { rerunBlocker } from './change-rules';

/** One row of the Changes log: what changed from what to what, in one sentence, where it stands and the one action it offers. */
export interface ChangeRow {
  id: string;
  event: ChangeEvent;
  /** The sentence's subject, set in 500: "Colorado rules", "COUN 625". */
  subject: string;
  /** The sentence after its subject: " changed from 2026 to 2026 revision 2". */
  rest: string;
  /** The change in a few words, for messages and the action's name: "Colorado rules, revision 2", "COUN 625, new catalog year". */
  name: string;
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
export function versionDifference(oldName: string, newName: string): { from: string; to: string; added: string } {
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

/** The row for one change event. `jurisdictionName` turns "CO" into "Colorado". */
export function changeRow(e: ChangeEvent, jurisdictionName: (code: string) => string, determinations: Determination[]): ChangeRow {
  const kind = CHANGE_KIND[e.kind];
  const base = {
    id: e.id, event: e, open: e.status === 'OPEN', action: kind.action, blocker: rerunBlocker(e),
    affects: affects(e.reopenedDeterminationIds, jurisdictionName, determinations), createdAt: e.createdAt,
  };
  if (e.kind === 'RULE_VERSION') {
    const subject = `${e.jurisdiction ? jurisdictionName(e.jurisdiction) : 'State'} rules`;
    const diff = e.oldLabel && e.newLabel ? versionDifference(e.oldLabel, e.newLabel) : null;
    return {
      ...base, subject, rest: diff ? ` changed from ${diff.from} to ${diff.to}` : ' have a new version',
      name: `${subject}, ${diff?.added || 'new version'}`,
      fromFull: e.oldLabel ?? 'Previous rule version', toFull: e.newLabel ?? 'New rule version', versionNoun: 'Rule version',
    };
  }
  const subject = e.courseCode ?? 'A course';
  const years = e.oldCatalogYear && e.newCatalogYear ? { from: e.oldCatalogYear, to: e.newCatalogYear } : null;
  return {
    ...base, subject, rest: years ? ` changed from catalog year ${years.from} to ${years.to}` : ' has a new catalog year',
    name: `${subject}, new catalog year`,
    fromFull: e.oldCatalogYear ?? 'Earlier catalog year', toFull: e.newCatalogYear ?? 'New catalog year', versionNoun: 'Catalog year',
  };
}

/** Newest first: the log reads from the latest change down. */
export function changeRows(events: ChangeEvent[], jurisdictionName: (code: string) => string, determinations: Determination[]): ChangeRow[] {
  return [...events].sort((x, y) => y.createdAt.localeCompare(x.createdAt)).map((e) => changeRow(e, jurisdictionName, determinations));
}
