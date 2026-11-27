import { describe, expect, it } from 'vitest';
import { beforeSignIn, navLinks } from './navigation';

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
    expect(navLinks('p1', NO_COUNTS, 'ADMIN').map((link) => link.id).slice(0, 3)).toEqual(['register', 'people', 'overview']);
  });
});

describe('beforeSignIn', () => {
  it('is true for sign-in and choosing a password, and nothing else', () => {
    expect(beforeSignIn('/login')).toBe(true);
    expect(beforeSignIn('/setup')).toBe(true);
    expect(beforeSignIn('/setup?code=abc')).toBe(true);
    expect(beforeSignIn('/setupx')).toBe(false);
    expect(beforeSignIn('/people')).toBe(false);
    expect(beforeSignIn('/programs/p1/overview')).toBe(false);
  });
});
