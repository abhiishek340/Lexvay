import { describe, expect, it } from 'vitest';
import { NavLink, beforeSignIn, inLink, navLinks, needsJurisdictionParam, tabBarOverflow, tabSlots } from './navigation';

const NO_COUNTS = { waitingForReview: 0, openRequests: 0, openChanges: 0, rulesWaiting: 0 };
const tenantIds = (role: Parameters<typeof navLinks>[2], programId: string | null = 'p1') =>
  navLinks(programId, NO_COUNTS, role).filter((link) => link.tenant).map((link) => link.id);

describe('navLinks', () => {
  it('lists People under the register for administrators only', () => {
    expect(tenantIds('ADMIN')).toEqual(['register', 'people']);
    expect(tenantIds('ADMIN', null)).toEqual(['register', 'people']);
    for (const role of ['CONTRIBUTOR', 'REVIEWER', 'SIGNER', null] as const) { expect(tenantIds(role)).toEqual(['register']); }
  });

  it('links People to its tenant-wide route with the users icon', () => {
    expect(navLinks(null, NO_COUNTS, 'ADMIN')[1]).toMatchObject({ path: ['/people'], label: 'People', short: 'People', icon: 'phosphorUsers' });
  });

  it('puts the program screens after the tenant-wide ones', () => {
    expect(navLinks('p1', NO_COUNTS, 'ADMIN').map((link) => link.id)).toEqual(
      ['register', 'people', 'overview', 'setup', 'review', 'coverage', 'requests', 'changes', 'rules', 'report', 'audit']);
  });
});

describe('beforeSignIn', () => {
  it('is true for sign-in and choosing a password, and nothing else', () => {
    expect(beforeSignIn('/login')).toBe(true);
    expect(beforeSignIn('/setup')).toBe(true);
    expect(beforeSignIn('/setup#code=abc')).toBe(true);
    expect(beforeSignIn('/setupx')).toBe(false);
    expect(beforeSignIn('/people')).toBe(false);
    expect(beforeSignIn('/programs/p1/overview')).toBe(false);
  });
});

describe('the bottom tab bar', () => {
  const ids = (links: NavLink[]) => links.map((link) => link.id);

  it('fits nine tabs in a 400 px window and more in a wider one', () => {
    expect(tabSlots(400)).toBe(9);
    expect(tabSlots(639)).toBe(15);
    expect(tabSlots(80)).toBe(2);
  });

  it('keeps every screen in the bar when they fit', () => {
    expect(tabBarOverflow(navLinks('p1', NO_COUNTS, 'REVIEWER'), 10)).toEqual([]);
  });

  it("moves the least used screens into More at 400 px; Program setup, just below Changes, stays on the bar", () => {
    for (const role of ['CONTRIBUTOR', 'REVIEWER', 'SIGNER'] as const) {
      const links = navLinks('p1', NO_COUNTS, role);
      expect(links).toHaveLength(10);
      expect(ids(tabBarOverflow(links, tabSlots(400)))).toEqual(['report', 'audit']);
    }
    const admin = navLinks('p1', NO_COUNTS, 'ADMIN');
    expect(admin).toHaveLength(11);
    expect(ids(tabBarOverflow(admin, tabSlots(400)))).toEqual(['people', 'report', 'audit']);
  });

  it('moves more as the room shrinks, least used first, keeping the rail order', () => {
    const links = navLinks('p1', NO_COUNTS, 'ADMIN');
    expect(ids(tabBarOverflow(links, 8))).toEqual(['people', 'rules', 'report', 'audit']);
    expect(ids(tabBarOverflow(links, 7))).toEqual(['register', 'people', 'rules', 'report', 'audit']);
    expect(ids(tabBarOverflow(links, 6))).toEqual(['register', 'people', 'setup', 'rules', 'report', 'audit']);
  });

  it('places Program setup by its priority alone, after Changes and before the register', () => {
    const links = navLinks('p1', NO_COUNTS, 'ADMIN');
    const priority = (id: string) => links.find((link) => link.id === id)!.priority;
    expect(priority('changes')).toBeGreaterThan(priority('setup'));
    expect(priority('setup')).toBeGreaterThan(priority('register'));
  });

  it('knows when the screen shown is one of the links, sub-pages included', () => {
    const rules = navLinks('p1', NO_COUNTS, 'ADMIN').find((link) => link.id === 'rules')!;
    expect(inLink('/programs/p1/rules', rules)).toBe(true);
    expect(inLink('/programs/p1/rules/rv7?new=1', rules)).toBe(true);
    expect(inLink('/programs/p1/rulesx', rules)).toBe(false);
    expect(inLink('/programs/p1/review', rules)).toBe(false);
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
