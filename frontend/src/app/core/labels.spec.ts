import { describe, expect, it } from 'vitest';
import { ACTION_LABEL, DECISION_LABEL, FINDING_GLYPH, FINDING_LABEL, OUTCOME_LABEL, actionLabel } from './labels';

describe('labels', () => {
  it('never use the forbidden phrases', () => {
    const all = [...Object.values(FINDING_LABEL), ...Object.values(DECISION_LABEL), ...Object.values(OUTCOME_LABEL), ...Object.values(ACTION_LABEL)].join(' ').toLowerCase();
    for (const forbidden of [['compl', 'iant'].join(''), ['meets requ', 'irements'].join('')]) {
      expect(all).not.toContain(forbidden);
    }
  });
  it('every finding has a distinct glyph so state is not colour alone', () => {
    expect(new Set(Object.values(FINDING_GLYPH)).size).toBe(3);
  });
  it('unknown actions fall back to readable text', () => {
    expect(actionLabel('SOMETHING_NEW')).toBe('something new');
  });
});
