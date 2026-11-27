import { describe, expect, it } from 'vitest';
import { answersText, awaitingAnswer, blockedReason, canAnswer, matchesRequestFilter, requestCounts, requestState } from './evidence-requests';
import { EvidenceRequest } from './models';

function row(id: string, status: EvidenceRequest['status'], stale = false, signed = false): EvidenceRequest {
  return {
    mappingId: id, jurisdiction: 'UT', requirementRef: 'UT-' + id, requirementTitle: 'T', status, stale, signed, requestNote: 'Please send it.',
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

  it('an open request a signed determination covers is blocked, whether or not it is also stale', () => {
    expect(requestState(row('1', 'OPEN', false, true))).toBe('BLOCKED');
    expect(blockedReason(row('1', 'OPEN', false, true))).toBe('SIGNED');
    expect(blockedReason(row('1', 'OPEN', true, true))).toBe('SIGNED');
    expect(blockedReason(row('1', 'OPEN', true))).toBe('STALE');
    expect(blockedReason(row('1', 'OPEN'))).toBeNull();
    expect(canAnswer(row('1', 'OPEN', false, true))).toBe(false);
  });

  it('an answered request is never blocked, signed or not', () => {
    expect(requestState(row('1', 'ANSWERED', false, true))).toBe('ANSWERED');
    expect(blockedReason(row('1', 'ANSWERED', true, true))).toBeNull();
  });

  it('an answered request waits for review and cannot be answered again', () => {
    expect(requestState(row('1', 'ANSWERED'))).toBe('ANSWERED');
    expect(requestState(row('1', 'ANSWERED', true))).toBe('ANSWERED');
    expect(canAnswer(row('1', 'ANSWERED'))).toBe(false);
  });

  it('filters and counts by the server status; a blocked request is still open', () => {
    const rows = [row('1', 'OPEN'), row('2', 'OPEN', true), row('3', 'ANSWERED'), row('4', 'OPEN', false, true)];
    expect(requestCounts(rows)).toEqual({ OPEN: 3, ANSWERED: 1, ALL: 4 });
    expect(rows.filter((r) => matchesRequestFilter(r, 'OPEN')).map((r) => r.mappingId)).toEqual(['1', '2', '4']);
    expect(rows.filter((r) => matchesRequestFilter(r, 'ANSWERED')).map((r) => r.mappingId)).toEqual(['3']);
    expect(rows.filter((r) => matchesRequestFilter(r, 'ALL'))).toHaveLength(4);
    expect(requestCounts([])).toEqual({ OPEN: 0, ANSWERED: 0, ALL: 0 });
  });

  it('the badge counts open requests that can be answered now or after a re-run, not those a signature covers', () => {
    const rows = [row('1', 'OPEN'), row('2', 'OPEN', true), row('3', 'ANSWERED'), row('4', 'OPEN', false, true), row('5', 'OPEN', true, true)];
    expect(awaitingAnswer(rows)).toBe(2);
    expect(awaitingAnswer([])).toBe(0);
  });

  it('says the number of answers in words for the cards that have no column header', () => {
    expect(answersText(0)).toBe('No answers');
    expect(answersText(1)).toBe('1 answer');
    expect(answersText(3)).toBe('3 answers');
  });
});
