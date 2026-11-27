import { describe, expect, it } from 'vitest';
import { MAX_TERMS, applyTerms, droppedTerms, normaliseTerm, sameTerms, termsDiff } from './search-terms';

describe('search term chips', () => {
  it('normalises an entry the way the server stores it', () => {
    expect(normaliseTerm('  Code   of\tEthics ')).toBe('code of ethics');
    expect(normaliseTerm('   ')).toBe('');
  });

  it('adds a typed entry, splits a pasted list on commas, and drops blanks and repeats quietly', () => {
    expect(applyTerms(['dsm', 'Psychopathology'])).toEqual({ terms: ['dsm', 'psychopathology'], problem: null });
    expect(applyTerms(['dsm', 'case conceptualization, DSM ,, clinical assessment'])).toEqual({
      terms: ['dsm', 'case conceptualization', 'clinical assessment'], problem: null,
    });
    expect(applyTerms(['dsm', '  '])).toEqual({ terms: ['dsm'], problem: null });
  });

  it('removing a chip leaves the others in order', () => {
    expect(applyTerms(['a', 'c'])).toEqual({ terms: ['a', 'c'], problem: null });
    expect(applyTerms([])).toEqual({ terms: [], problem: null });
  });

  it('does not take a term over 40 characters, and says why', () => {
    const long = 'x'.repeat(41);
    expect(applyTerms(['dsm', long])).toEqual({ terms: ['dsm'], problem: 'A search term has at most 40 characters.' });
    expect(applyTerms(['y'.repeat(40)]).terms).toEqual(['y'.repeat(40)]);
  });

  it('keeps at most twelve terms, and says why when more are offered', () => {
    const twelve = Array.from({ length: MAX_TERMS }, (_, i) => `term ${String.fromCharCode(97 + i)}`);
    expect(applyTerms(twelve)).toEqual({ terms: twelve, problem: null });
    expect(applyTerms([...twelve, 'one more'])).toEqual({ terms: twelve, problem: 'A requirement has at most 12 search terms.' });
    // A repeat of a kept term is not "more".
    expect(applyTerms([...twelve, 'TERM A']).problem).toBeNull();
  });

  it('compares lists in order', () => {
    expect(sameTerms(['a', 'b'], ['a', 'b'])).toBe(true);
    expect(sameTerms(['a', 'b'], ['b', 'a'])).toBe(false);
    expect(sameTerms(['a'], ['a', 'b'])).toBe(false);
  });

  it('marks the removed terms on the old side and the added ones on the new side', () => {
    expect(termsDiff(['dsm', 'psychopathology'], ['psychopathology', 'case conceptualization'])).toEqual({
      before: [{ term: 'dsm', changed: true }, { term: 'psychopathology', changed: false }],
      after: [{ term: 'psychopathology', changed: false }, { term: 'case conceptualization', changed: true }],
    });
    expect(termsDiff([], ['a'])).toEqual({ before: [], after: [{ term: 'a', changed: true }] });
  });

  it('counts the terms the server did not store', () => {
    expect(droppedTerms([['ethics', 'confidentiality'], ['utah law']], [['confidentiality'], []])).toBe(2);
    expect(droppedTerms([['a']], [['a']])).toBe(0);
  });
});
