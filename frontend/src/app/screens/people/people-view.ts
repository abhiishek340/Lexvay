import { formatDate } from '@angular/common';
import { ROLE_LABEL } from '../../core/labels';
import { Account, Role } from '../../core/models';

/** Where an account stands: deactivated wins over everything, then an account nobody has set a password for. */
export type PersonStatus = 'ACTIVE' | 'PENDING' | 'DEACTIVATED';

export function personStatus(account: Pick<Account, 'active' | 'passwordSet'>): PersonStatus {
  if (!account.active) { return 'DEACTIVATED'; }
  return account.passwordSet ? 'ACTIVE' : 'PENDING';
}

/** A status in words; anything but Active steps back to the secondary text colour. */
export const PERSON_STATUS: Record<PersonStatus, { label: string; quiet: boolean }> = {
  ACTIVE: { label: 'Active', quiet: false },
  PENDING: { label: 'Setup pending', quiet: true },
  DEACTIVATED: { label: 'Deactivated', quiet: true },
};

/** Roles in the order of what they may do, least first (the Select and the role filter). */
export const ROLES: readonly Role[] = ['CONTRIBUTOR', 'REVIEWER', 'SIGNER', 'ADMIN'];

/** One line per role, as the server's permissions stand: People's Help and the role options of the dialog. */
export const ROLE_DESCRIPTION: Record<Role, string> = {
  CONTRIBUTOR: 'Adds programs, courses, documents and rule versions.',
  REVIEWER: 'Reviews findings and approves extracted requirements.',
  SIGNER: 'Reviews findings and signs determinations.',
  ADMIN: 'Manages people and can do everything but sign.',
};

export const ROLE_OPTIONS = ROLES.map((value) => ({ value, label: ROLE_LABEL[value] }));
export const STATUS_OPTIONS = (Object.keys(PERSON_STATUS) as PersonStatus[]).map((value) => ({ value, label: PERSON_STATUS[value].label }));

export interface PeopleQuery { readonly text: string; readonly role: Role | null; readonly status: PersonStatus | null }
export const NO_PEOPLE_QUERY: PeopleQuery = { text: '', role: null, status: null };

export function filtered(query: PeopleQuery): boolean {
  return query.text.trim() !== '' || query.role !== null || query.status !== null;
}

/** The accounts that match every filter on screen; the text matches anywhere in the name or the email, ignoring case. */
export function filterPeople(accounts: readonly Account[], query: PeopleQuery): Account[] {
  const text = query.text.trim().toLocaleLowerCase();
  return accounts.filter((a) =>
    (query.role === null || a.role === query.role)
    && (query.status === null || personStatus(a) === query.status)
    && (text === '' || a.displayName.toLocaleLowerCase().includes(text) || a.email.toLocaleLowerCase().includes(text)));
}

/** The list after a change: the account replaced or added, in the server's order (display name, then email). */
export function upsert(accounts: readonly Account[], account: Account): Account[] {
  return [...accounts.filter((a) => a.id !== account.id), account]
    .sort((a, b) => a.displayName.localeCompare(b.displayName) || a.email.localeCompare(b.email));
}

export type PersonAction = 'role' | 'deactivate' | 'reactivate' | 'setup-code';

/**
 * What the row menu offers. An administrator's own row has neither a role change nor a deactivation: the server
 * refuses both (D-375), so the screen does not offer them.
 */
export function personActions(account: Account, selfId: string | null): PersonAction[] {
  if (account.id === selfId) { return ['setup-code']; }
  return ['role', account.active ? 'deactivate' : 'reactivate', 'setup-code'];
}

/**
 * The link the person opens to choose a password. The code travels in the fragment, which the browser never sends to a
 * server, so no request or access log carries it; the setup page reads it and clears it (D-435).
 */
export function setupLink(origin: string, code: string): string {
  return `${origin}/setup#code=${encodeURIComponent(code)}`;
}

/** "Valid for 72 hours, until 30 Nov 15:04", in the viewer's time zone. */
export function expiryText(expiresAt: string, now: Date): string {
  const end = new Date(expiresAt);
  const hours = Math.max(0, Math.round((end.getTime() - now.getTime()) / 3_600_000));
  return `Valid for ${hours} ${hours === 1 ? 'hour' : 'hours'}, until ${formatDate(end, 'd MMM HH:mm', 'en-US')}`;
}

/** "6 people", "1 of 6 people" once a filter hides some. */
export function peopleCount(shown: number, total: number): string {
  const noun = total === 1 ? 'person' : 'people';
  return shown === total ? `${total} ${noun}` : `${shown} of ${total} ${noun}`;
}

export interface PersonProblems { readonly name?: string; readonly email?: string }

/** The add form's own checks, before the server's (which also refuses an email already in use, 409). */
export function personProblems(name: string, email: string): PersonProblems {
  const problems: { name?: string; email?: string } = {};
  if (name.trim() === '') { problems.name = 'Enter a name.'; }
  if (email.trim() === '') {
    problems.email = 'Enter an email address.';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
    problems.email = 'Enter an email address like name@university.edu.';
  }
  return problems;
}
