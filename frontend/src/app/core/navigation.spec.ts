import { describe, expect, it } from 'vitest';
import { needsJurisdictionParam, sameScreen, screenOf } from './navigation';

describe('screenOf and sameScreen', () => {
  it('names a screen by the path up to its own segment, ignoring the query and fragment', () => {
    expect(screenOf('/programs/p1/rules/rv7?x=1#top')).toBe('/programs/p1/rules');
    expect(sameScreen('/programs/p1/review', '/programs/p1/review?jurisdiction=CA')).toBe(true);
    expect(sameScreen('/programs/p1/review', '/programs/p1/coverage')).toBe(false);
  });
});

describe('needsJurisdictionParam', () => {
  it('leaves the URL alone for the first state when it carries no parameter, the default', () => {
    expect(needsJurisdictionParam(null, 'AZ', 'AZ')).toBe(false);
  });

  it('writes the parameter for any other state, and again when the URL names a different one', () => {
    expect(needsJurisdictionParam(null, 'CA', 'AZ')).toBe(true);
    expect(needsJurisdictionParam('CA', 'AZ', 'AZ')).toBe(true);
  });

  it('does nothing when the URL already names the state', () => {
    expect(needsJurisdictionParam('CA', 'CA', 'AZ')).toBe(false);
  });
});
