import { RegisterFilter } from './models';

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
