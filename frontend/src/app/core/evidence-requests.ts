import type { IconName } from '../theme/icons';
import { EvidenceRequest } from './models';

/**
 * What the inbox shows for a request. Blocked: the request is open but its mapping went stale after a rule or
 * course change, so the server refuses an answer (409) until the change is re-run.
 */
export type RequestState = 'OPEN' | 'ANSWERED' | 'BLOCKED';

export type RequestFilter = 'OPEN' | 'ANSWERED' | 'ALL';

export const REQUEST_FILTERS: { value: RequestFilter; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'ANSWERED', label: 'Answered' },
  { value: 'ALL', label: 'All' },
];

export const REQUEST_STATE: Record<RequestState, { label: string; icon: IconName; severity: 'info' | 'success' | 'warn' }> = {
  OPEN: { label: 'Open', icon: 'phosphorEnvelopeOpen', severity: 'info' },
  ANSWERED: { label: 'Answered, awaiting review', icon: 'phosphorCheckCircle', severity: 'success' },
  BLOCKED: { label: 'Blocked', icon: 'phosphorArrowsClockwise', severity: 'warn' },
};

export function requestState(row: EvidenceRequest): RequestState {
  if (row.status === 'OPEN') {
    return row.stale ? 'BLOCKED' : 'OPEN';
  }
  return 'ANSWERED';
}

/** A blocked request is still an open request: it is listed, and counted, under Open. */
export function matchesRequestFilter(row: EvidenceRequest, filter: RequestFilter): boolean {
  return filter === 'ALL' || row.status === filter;
}

export function requestCounts(rows: readonly EvidenceRequest[]): Record<RequestFilter, number> {
  const open = rows.filter((r) => r.status === 'OPEN').length;
  return { OPEN: open, ANSWERED: rows.length - open, ALL: rows.length };
}

/** Only an open request that is not blocked can be answered. */
export function canAnswer(row: EvidenceRequest): boolean {
  return requestState(row) === 'OPEN';
}
