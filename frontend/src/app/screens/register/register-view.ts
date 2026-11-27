import { OUTCOME_LABEL } from '../../core/labels';
import { DeterminationLists, DisclosureEntry } from '../../core/models';

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

export interface DisclosureSection { id: 'meet' | 'not-meet' | 'none'; title: string; empty: string; rows: DisclosureRow[] }

export const NO_CURRENT_SIGNATURE_NOTE = 'No current signed determination';
export const SIGNED_NO_DETERMINATION_NOTE = 'Signed as no determination made';

/** The three lists in their regulatory order, titled with the outcome labels used in signing. */
export function disclosureSections(lists: DeterminationLists): DisclosureSection[] {
  return [
    { id: 'meet', title: OUTCOME_LABEL.MEETS, empty: 'No target jurisdiction has a current signed determination to meet.', rows: lists.determinedToMeet.map(disclosureRow) },
    { id: 'not-meet', title: OUTCOME_LABEL.DOES_NOT_MEET, empty: 'No target jurisdiction has a current signed determination not to meet.', rows: lists.determinedNotToMeet.map(disclosureRow) },
    { id: 'none', title: OUTCOME_LABEL.NOT_DETERMINED, empty: 'Every target jurisdiction has a current signed determination to meet or not to meet.', rows: lists.noDeterminationMade.map(disclosureRow) },
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
