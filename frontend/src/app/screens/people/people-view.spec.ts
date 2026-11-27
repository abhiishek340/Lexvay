import { describe, expect, it } from 'vitest';
import { Account } from '../../core/models';
import {
  NO_PEOPLE_QUERY, PERSON_STATUS, ROLES, ROLE_DESCRIPTION, STATUS_OPTIONS, expiryText, filterPeople, filtered, peopleCount, personActions,
  personProblems, personStatus, setupLink, upsert,
} from './people-view';

const account = (over: Partial<Account>): Account => ({
  id: 'u1', displayName: 'Dana Whitfield', email: 'dana.whitfield@northfield.example', role: 'REVIEWER', active: true,
  createdAt: '2026-11-01T09:00:00Z', passwordSet: true, ...over,
});

const PEOPLE: Account[] = [
  account({ id: 'a', displayName: 'Avery Admin', email: 'admin@northfield.example', role: 'ADMIN' }),
  account({ id: 'd', displayName: 'Dana Whitfield' }),
  account({ id: 'j', displayName: 'Jordan Ellis', email: 'jordan.ellis@northfield.example', role: 'CONTRIBUTOR', passwordSet: false }),
  account({ id: 'p', displayName: 'Priya Raman', email: 'priya.raman@northfield.example', role: 'SIGNER', active: false }),
];
const ids = (list: Account[]) => list.map((a) => a.id);

describe('personStatus', () => {
  it('is deactivated first, then setup pending without a password, else active', () => {
    expect(personStatus({ active: true, passwordSet: true })).toBe('ACTIVE');
    expect(personStatus({ active: true, passwordSet: false })).toBe('PENDING');
    expect(personStatus({ active: false, passwordSet: false })).toBe('DEACTIVATED');
    expect(personStatus({ active: false, passwordSet: true })).toBe('DEACTIVATED');
  });

  it('gives every status a word and an icon, and the filter the same words', () => {
    expect(STATUS_OPTIONS.map((o) => o.label)).toEqual(['Active', 'Setup pending', 'Deactivated']);
    for (const status of Object.values(PERSON_STATUS)) { expect(status.icon).toMatch(/^phosphor/); }
  });

  it('describes every role in one line', () => {
    for (const role of ROLES) { expect(ROLE_DESCRIPTION[role]).toMatch(/^[A-Z][^.]+\.$/); }
  });
});

describe('filterPeople', () => {
  it('shows everyone without a filter', () => {
    expect(filtered(NO_PEOPLE_QUERY)).toBe(false);
    expect(ids(filterPeople(PEOPLE, NO_PEOPLE_QUERY))).toEqual(['a', 'd', 'j', 'p']);
  });

  it('matches the text anywhere in the name or the email, ignoring case and outer spaces', () => {
    expect(ids(filterPeople(PEOPLE, { ...NO_PEOPLE_QUERY, text: '  RAMAN ' }))).toEqual(['p']);
    expect(ids(filterPeople(PEOPLE, { ...NO_PEOPLE_QUERY, text: 'ellis@' }))).toEqual(['j']);
    expect(ids(filterPeople(PEOPLE, { ...NO_PEOPLE_QUERY, text: 'northfield' }))).toEqual(['a', 'd', 'j', 'p']);
    expect(filtered({ ...NO_PEOPLE_QUERY, text: '   ' })).toBe(false);
  });

  it('filters by role and by status, and combines every filter', () => {
    expect(ids(filterPeople(PEOPLE, { ...NO_PEOPLE_QUERY, role: 'SIGNER' }))).toEqual(['p']);
    expect(ids(filterPeople(PEOPLE, { ...NO_PEOPLE_QUERY, status: 'PENDING' }))).toEqual(['j']);
    expect(ids(filterPeople(PEOPLE, { ...NO_PEOPLE_QUERY, status: 'ACTIVE' }))).toEqual(['a', 'd']);
    expect(ids(filterPeople(PEOPLE, { text: 'a', role: 'REVIEWER', status: 'ACTIVE' }))).toEqual(['d']);
    expect(ids(filterPeople(PEOPLE, { text: 'priya', role: 'SIGNER', status: 'ACTIVE' }))).toEqual([]);
    expect(filtered({ ...NO_PEOPLE_QUERY, status: 'DEACTIVATED' })).toBe(true);
  });

  it('counts what is shown against everyone', () => {
    expect(peopleCount(4, 4)).toBe('4 people');
    expect(peopleCount(1, 4)).toBe('1 of 4 people');
    expect(peopleCount(1, 1)).toBe('1 person');
  });
});

describe('upsert', () => {
  it('replaces a changed account in place and adds a new one in name order', () => {
    const changed = upsert(PEOPLE, account({ id: 'd', role: 'SIGNER' }));
    expect(ids(changed)).toEqual(['a', 'd', 'j', 'p']);
    expect(changed[1].role).toBe('SIGNER');
    expect(ids(upsert(PEOPLE, account({ id: 'n', displayName: 'Morgan Blake', email: 'morgan@northfield.example' })))).toEqual(['a', 'd', 'j', 'n', 'p']);
  });
});

describe('personActions', () => {
  it('offers a role change, deactivation or reactivation and a new code on other rows', () => {
    expect(personActions(PEOPLE[1], 'a')).toEqual(['role', 'deactivate', 'setup-code']);
    expect(personActions(PEOPLE[3], 'a')).toEqual(['role', 'reactivate', 'setup-code']);
  });

  it("offers neither a role change nor deactivation on the administrator's own row", () => {
    expect(personActions(PEOPLE[0], 'a')).toEqual(['setup-code']);
  });
});

describe('the one-time link', () => {
  it('carries the code in the fragment of the setup page, never in what is sent to the server', () => {
    expect(setupLink('https://lexvay.northfield.example', 'Ab_-9z')).toBe('https://lexvay.northfield.example/setup#code=Ab_-9z');
    expect(setupLink('http://localhost:4302', 'a+b/c')).toBe('http://localhost:4302/setup#code=a%2Bb%2Fc');
  });

  it('states how long it lasts and until when, in local time', () => {
    const now = new Date(2026, 10, 27, 15, 4);
    const expires = new Date(2026, 10, 30, 15, 4).toISOString();
    expect(expiryText(expires, now)).toBe('Valid for 72 hours, until 30 Nov 15:04');
    expect(expiryText(new Date(2026, 10, 27, 16, 4).toISOString(), now)).toBe('Valid for 1 hour, until 27 Nov 16:04');
  });
});

describe('personProblems', () => {
  it('asks for a name and an email address', () => {
    expect(personProblems('', ' ')).toEqual({ name: 'Enter a name.', email: 'Enter an email address.' });
    expect(personProblems('Morgan Blake', 'morgan')).toEqual({ email: 'Enter an email address like name@university.edu.' });
    expect(personProblems(' Morgan Blake ', ' morgan@northfield.example ')).toEqual({});
  });
});
