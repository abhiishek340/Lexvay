import { OUTCOME_LABEL } from './labels';
import { DeterminationLists, DisclosureEntry, RegisterFilter } from './models';

/** The register's endpoints share their filters; the export is the same query as CSV. */
export const REGISTER_PATH = '/api/v1/determinations/register';

export const NO_FILTER: RegisterFilter = { programId: null, jurisdiction: null, outcome: null, includeReopened: false };

/** Query parameters for a filter: only the filters that are set; `current` only when reopened ones are included. */
export function registerQuery(filter: RegisterFilter): Record<string, string> {
  const query: Record<string, string> = {};
  if (filter.programId) { query['programId'] = filter.programId; }
  if (filter.jurisdiction) { query['jurisdiction'] = filter.jurisdiction; }
  if (filter.outcome) { query['outcome'] = filter.outcome; }
  if (filter.includeReopened) { query['current'] = 'false'; }
  return query;
}

/** The "Download CSV" link for the filters on screen. */
export function registerCsvUrl(filter: RegisterFilter): string {
  const query = new URLSearchParams(registerQuery(filter)).toString();
  return `${REGISTER_PATH}.csv${query ? `?${query}` : ''}`;
}

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
