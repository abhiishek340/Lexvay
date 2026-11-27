import { describe, expect, it } from 'vitest';
import { STORY_MS, STORY_STILL, storyAt } from './sign-in-story';

describe('the sign-in story', () => {
  it('starts empty and fills from west to east', () => {
    expect(storyAt(0).map((s) => s.code)).toEqual(['WA']);
    expect(storyAt(500).map((s) => s.code)).toEqual(['WA', 'OR', 'CA']);
  });

  it('moves a state from in review, with its fill growing, to signed', () => {
    const wa = (t: number) => storyAt(t).find((s) => s.code === 'WA')!;
    expect(wa(100)).toMatchObject({ tile: 'IN_REVIEW', reviewed: 0 });
    expect(wa(1300).reviewed).toBeGreaterThan(wa(400).reviewed);
    expect(wa(2400)).toMatchObject({ tile: 'IN_REVIEW', reviewed: 8 });
    expect(wa(2700)).toMatchObject({ tile: 'MEETS', reviewed: 8 });
  });

  it('shows every outcome at its fullest moment, then fades back and repeats', () => {
    expect(new Set(STORY_STILL.map((s) => s.tile))).toEqual(new Set(['MEETS', 'DOES_NOT_MEET', 'NOT_DETERMINED', 'IN_REVIEW']));
    expect(storyAt(5800)).toEqual([]);
    expect(storyAt(STORY_MS + 500)).toEqual(storyAt(500));
  });

  it('never counts past the total', () => {
    for (let t = 0; t < STORY_MS; t += 250) {
      for (const s of storyAt(t)) { expect(s.reviewed).toBeLessThanOrEqual(s.total); }
    }
  });
});
