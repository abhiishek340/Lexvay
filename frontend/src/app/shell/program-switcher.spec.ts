import { describe, expect, it } from 'vitest';
import { truncateAtWord } from './program-switcher';

describe('truncateAtWord', () => {
  it('keeps a name that fits', () => {
    expect(truncateAtWord('M.S. Clinical Mental Health Counseling', 44)).toBe('M.S. Clinical Mental Health Counseling');
  });

  it('cuts after the last whole word and adds an ellipsis', () => {
    expect(truncateAtWord('M.S. Clinical Mental Health Counseling', 30)).toBe('M.S. Clinical Mental Health…');
  });

  it('drops trailing punctuation before the ellipsis', () => {
    expect(truncateAtWord('Counseling, School and Community', 13)).toBe('Counseling…');
  });

  it('cuts a single long word where it must', () => {
    expect(truncateAtWord('Psychopharmacology', 10)).toBe('Psychopha…');
  });
});
