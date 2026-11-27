import { describe, expect, it } from 'vitest';
import { commandScore, fuzzyScore } from './fuzzy';

describe('fuzzyScore', () => {
  it('is zero unless every query character appears in order', () => {
    expect(fuzzyScore('rvw', 'Review')).toBeGreaterThan(0);
    expect(fuzzyScore('wvr', 'Review')).toBe(0);
    expect(fuzzyScore('reviews', 'Review')).toBe(0);
    expect(fuzzyScore('', 'Review')).toBe(0);
  });

  it('ignores case and spaces in the query', () => {
    expect(fuzzyScore('REV CA', 'Review California')).toBe(fuzzyScore('revca', 'review california'));
  });

  it('prefers matches at word starts', () => {
    expect(fuzzyScore('ca', 'Review California')).toBeGreaterThan(fuzzyScore('ca', 'Medical records'));
    expect(fuzzyScore('er', 'Evidence report')).toBeGreaterThan(fuzzyScore('er', 'Determination register'));
  });

  it('prefers consecutive characters to scattered ones', () => {
    expect(fuzzyScore('rul', 'Rules')).toBeGreaterThan(fuzzyScore('rul', 'Run assessment, latest'));
  });

  it('finds the best placement, not the first one', () => {
    // A greedy match would take the "c" of "Medical"; the word start of "California" scores higher.
    expect(fuzzyScore('cal', 'Medical California')).toBeGreaterThan(fuzzyScore('cal', 'Medical'));
  });

  it('breaks ties in favour of the shorter text', () => {
    expect(fuzzyScore('audit', 'Audit trail')).toBeGreaterThan(fuzzyScore('audit', 'Audit trail of everything'));
  });

  it('treats a capital inside a word as a word start', () => {
    expect(fuzzyScore('cr', 'CaliforniaRules')).toBeGreaterThan(fuzzyScore('cr', 'Californiarules'));
  });
});

describe('commandScore', () => {
  it('matches keywords such as state codes and requirement refs', () => {
    expect(commandScore('tx', 'Review Texas', ['TX'])).toBeGreaterThan(0);
    expect(commandScore('ca-3', 'Telehealth competence', ['CA-3'])).toBeGreaterThan(0);
  });

  it('ranks a label match above the same keyword match', () => {
    expect(commandScore('rev', 'Review', [])).toBeGreaterThan(commandScore('rev', 'Program overview', ['Review']));
  });
});
