import { describe, expect, it } from 'vitest';
import { canAnswer, matchesRequestFilter, requestCounts, requestState } from './evidence-requests';
import { EvidenceRequest } from './models';

function row(id: string, status: EvidenceRequest['status'], stale = false): EvidenceRequest {
  return {
    mappingId: id, jurisdiction: 'UT', requirementRef: 'UT-' + id, requirementTitle: 'T', status, stale, requestNote: 'Please send it.',
    requestedBy: 'u1', requestedByName: 'Dr. Priya Raman', requestedAt: '2026-11-26T10:00:00Z', responseCount: 0, latestResponse: null,
  };
}

describe('evidence request status', () => {
  it('an open request on a current mapping is open and can be answered', () => {
    expect(requestState(row('1', 'OPEN'))).toBe('OPEN');
    expect(canAnswer(row('1', 'OPEN'))).toBe(true);
  });

  it('an open request on a stale mapping is blocked until the change is re-run', () => {
    expect(requestState(row('1', 'OPEN', true))).toBe('BLOCKED');
    expect(canAnswer(row('1', 'OPEN', true))).toBe(false);
  });

  it('an answered request waits for review and cannot be answered again', () => {
    expect(requestState(row('1', 'ANSWERED'))).toBe('ANSWERED');
    expect(requestState(row('1', 'ANSWERED', true))).toBe('ANSWERED');
    expect(canAnswer(row('1', 'ANSWERED'))).toBe(false);
  });

  it('filters and counts by the server status; a blocked request is still open', () => {
    const rows = [row('1', 'OPEN'), row('2', 'OPEN', true), row('3', 'ANSWERED')];
    expect(requestCounts(rows)).toEqual({ OPEN: 2, ANSWERED: 1, ALL: 3 });
    expect(rows.filter((r) => matchesRequestFilter(r, 'OPEN')).map((r) => r.mappingId)).toEqual(['1', '2']);
    expect(rows.filter((r) => matchesRequestFilter(r, 'ANSWERED')).map((r) => r.mappingId)).toEqual(['3']);
    expect(rows.filter((r) => matchesRequestFilter(r, 'ALL'))).toHaveLength(3);
    expect(requestCounts([])).toEqual({ OPEN: 0, ANSWERED: 0, ALL: 0 });
  });
});
