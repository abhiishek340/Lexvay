import { describe, expect, it } from 'vitest';
import { navLinks, onPhoneBar } from './navigation';

const counts = { waitingForReview: 0, openRequests: 0, openChanges: 0, rulesWaiting: 0 };

describe('navLinks', () => {
  it('lists the register, then the program screens in rail order', () => {
    expect(navLinks('p1', counts).map((l) => l.id)).toEqual(
      ['register', 'overview', 'setup', 'review', 'coverage', 'requests', 'changes', 'rules', 'report', 'audit']);
    expect(navLinks(null, counts).map((l) => l.id)).toEqual(['register']);
  });

  it('keeps five screens on the phone bar and the rest behind More', () => {
    const links = navLinks('p1', counts);
    expect(links.filter(onPhoneBar).map((l) => l.id)).toEqual(['overview', 'review', 'requests', 'changes', 'rules']);
    expect(links.filter((l) => !onPhoneBar(l)).map((l) => l.id)).toEqual(['register', 'setup', 'coverage', 'report', 'audit']);
  });
});
