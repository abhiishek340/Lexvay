import { OUTCOME_LABEL } from '../../core/labels';
import { DeterminationLists, DisclosureEntry, Outcome, RegisterRow } from '../../core/models';
import { MapState } from '../../ui/state-map/state-map-model';

export interface DisclosureRow {
  code: string;
  name: string;
  /** The current signature, or null when there is none. */
  signedByName: string | null;
  signedAt: string | null;
  ruleVersionLabel: string | null;
  /** What the row says in place of, or next to, a signature. */
  note: string | null;
  /** When the latest signature was reopened by a change (only without a current signature). */
  reopenedAt: string | null;
}

/** One of the three lists; `outcome` is what a signature in it recorded (the seal of each signed row). */
export interface DisclosureSection { id: 'meet' | 'not-meet' | 'none'; outcome: Outcome; title: string; empty: string; rows: DisclosureRow[] }

/** What the three lists are, behind an InfoTip on screen and printed in full on paper. */
export const DISCLOSURE_NOTE = 'These lists show signed records only. Each target jurisdiction of the program appears once: under the outcome a signer '
  + 'recorded, or under "No determination made" when it has no current signed determination. The institution\'s published disclosure remains its '
  + 'own responsibility.';

export const NO_CURRENT_SIGNATURE_NOTE = 'No current signed determination';
export const SIGNED_NO_DETERMINATION_NOTE = 'Signed as no determination made';

/** The three lists in their regulatory order, titled with the outcome labels used in signing. */
export function disclosureSections(lists: DeterminationLists): DisclosureSection[] {
  return [
    { id: 'meet', outcome: 'MEETS', title: OUTCOME_LABEL.MEETS, empty: 'No target jurisdiction has a current signed determination to meet.', rows: lists.determinedToMeet.map(disclosureRow) },
    { id: 'not-meet', outcome: 'DOES_NOT_MEET', title: OUTCOME_LABEL.DOES_NOT_MEET, empty: 'No target jurisdiction has a current signed determination not to meet.', rows: lists.determinedNotToMeet.map(disclosureRow) },
    { id: 'none', outcome: 'NOT_DETERMINED', title: OUTCOME_LABEL.NOT_DETERMINED, empty: 'Every target jurisdiction has a current signed determination to meet or not to meet.', rows: lists.noDeterminationMade.map(disclosureRow) },
  ];
}

function disclosureRow(entry: DisclosureEntry): DisclosureRow {
  const note = entry.reason === 'NO_CURRENT_SIGNATURE' ? NO_CURRENT_SIGNATURE_NOTE
    : entry.reason === 'SIGNED_NO_DETERMINATION' ? SIGNED_NO_DETERMINATION_NOTE : null;
  return {
    code: entry.jurisdictionCode,
    name: entry.jurisdictionName,
    signedByName: entry.signedByName,
    signedAt: entry.signedAt,
    ruleVersionLabel: entry.ruleVersionLabel,
    note,
    reopenedAt: entry.reason === 'NO_CURRENT_SIGNATURE' && entry.reopenedDeterminationId ? entry.reopenedAt : null,
  };
}

/**
 * The register's map: every state with a current signature among the rows on screen, coloured by its outcome. A
 * state signed in one program opens that program's evidence report; signed in several, it shows their common
 * outcome, or "Different outcomes" when they differ, and the table lists them. Reopened rows are not current and
 * colour nothing.
 */
export function registerMapStates(rows: readonly RegisterRow[]): MapState[] {
  const byState = new Map<string, RegisterRow[]>();
  for (const row of rows.filter((r) => r.status === 'SIGNED')) {
    byState.set(row.jurisdictionCode, [...(byState.get(row.jurisdictionCode) ?? []), row]);
  }
  return [...byState].map(([code, signed]) => {
    const outcomes = new Set(signed.map((r) => r.outcome));
    const outcome = outcomes.size === 1 ? signed[0].outcome : null;
    const base = { code, reviewed: 0, total: 0 };
    if (signed.length === 1) {
      const [row] = signed;
      return { ...base, tile: row.outcome, status: OUTCOME_LABEL[row.outcome],
        action: { label: 'View report', commands: ['/programs', row.programId, 'report'], queryParams: { jurisdiction: code } } };
    }
    return outcome
      ? { ...base, tile: outcome, status: `${OUTCOME_LABEL[outcome]} in ${signed.length} programs`, action: null }
      : { ...base, tile: 'MIXED', status: `Different outcomes in ${signed.length} programs`, action: null };
  });
}
