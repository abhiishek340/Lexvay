import { EvidenceRequest } from './models';

/**
 * What the inbox shows for a request. Blocked: the request is open but the server refuses an answer (409), because
 * its mapping went stale after a rule or course change (until the change is re-run) or because a signed
 * determination already covers it (FIX-3).
 */
export type RequestState = 'OPEN' | 'ANSWERED' | 'BLOCKED';

/** Why an open request cannot be answered; a signature outranks staleness, since a re-run does not lift it. */
export type BlockedReason = 'SIGNED' | 'STALE';

export type RequestFilter = 'OPEN' | 'ANSWERED' | 'ALL';

export const REQUEST_FILTERS: { value: RequestFilter; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'ANSWERED', label: 'Answered' },
  { value: 'ALL', label: 'All' },
];

export function requestState(row: EvidenceRequest): RequestState {
  if (row.status === 'OPEN') {
    return blockedReason(row) ? 'BLOCKED' : 'OPEN';
  }
  return 'ANSWERED';
}

/** Null for a request that can be answered or is already answered. */
export function blockedReason(row: EvidenceRequest): BlockedReason | null {
  if (row.status !== 'OPEN') { return null; }
  if (row.signed) { return 'SIGNED'; }
  return row.stale ? 'STALE' : null;
}

/** A blocked request is still an open request: it is listed, and counted, under Open. */
export function matchesRequestFilter(row: EvidenceRequest, filter: RequestFilter): boolean {
  return filter === 'ALL' || row.status === filter;
}

export function requestCounts(rows: readonly EvidenceRequest[]): Record<RequestFilter, number> {
  const open = rows.filter((r) => r.status === 'OPEN').length;
  return { OPEN: open, ANSWERED: rows.length - open, ALL: rows.length };
}

/**
 * The rail badge: open requests that still need an answer, now or after a re-run. A request a signed determination
 * covers can never be answered, so it is listed (blocked) but not counted, as the work queue does (FIX-3).
 */
export function awaitingAnswer(rows: readonly EvidenceRequest[]): number {
  return rows.filter((r) => r.status === 'OPEN' && !r.signed).length;
}

/** Only an open request that is not blocked can be answered. */
export function canAnswer(row: EvidenceRequest): boolean {
  return requestState(row) === 'OPEN';
}

/** "No answers", "1 answer", "3 answers": the count in words, where no column header names it (the inbox's cards). */
export function answersText(count: number): string {
  return count === 0 ? 'No answers' : `${count} ${count === 1 ? 'answer' : 'answers'}`;
}
