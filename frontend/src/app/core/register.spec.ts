import { describe, expect, it } from 'vitest';
import { NO_FILTER, registerCsvUrl, registerQuery } from './register';

describe('register query and CSV link', () => {
  it('sends no filter and no current flag by default, so the server keeps only signed determinations', () => {
    expect(registerQuery(NO_FILTER)).toEqual({});
    expect(registerCsvUrl(NO_FILTER)).toBe('/api/v1/determinations/register.csv');
  });
  it('carries every filter on screen, with current=false when reopened determinations are included', () => {
    const filter = { programId: 'p 1', jurisdiction: 'CA', outcome: 'DOES_NOT_MEET' as const, includeReopened: true };
    expect(registerQuery(filter)).toEqual({ programId: 'p 1', jurisdiction: 'CA', outcome: 'DOES_NOT_MEET', current: 'false' });
    expect(registerCsvUrl(filter)).toBe('/api/v1/determinations/register.csv?programId=p+1&jurisdiction=CA&outcome=DOES_NOT_MEET&current=false');
  });
  it('leaves out the filters that are not set', () => {
    expect(registerCsvUrl({ ...NO_FILTER, outcome: 'MEETS' })).toBe('/api/v1/determinations/register.csv?outcome=MEETS');
  });
});
