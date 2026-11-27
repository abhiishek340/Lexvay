/** The server's limits (D-402), counted as the server counts them: in code points, so an emoji is one character. */
export const MIN_CHARACTERS = 12;
export const MAX_CHARACTERS = 128;

export type RuleId = 'length' | 'email' | 'match';
export interface PasswordRule { readonly id: RuleId; readonly label: string; readonly met: boolean }

/**
 * Anything shaped like an email address. The page knows only the setup code, not the account's email, so it refuses
 * every address: that includes the person's own, which is the server's rule (D-428).
 */
const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function characters(text: string): number {
  return [...text].length;
}

/** The checklist under the fields, in reading order; the server checks the same rules again. */
export function passwordRules(password: string, repeat: string): PasswordRule[] {
  const length = characters(password);
  return [
    { id: 'length', label: `${MIN_CHARACTERS} to ${MAX_CHARACTERS} characters`, met: length >= MIN_CHARACTERS && length <= MAX_CHARACTERS },
    { id: 'email', label: 'Not an email address', met: password.length > 0 && !EMAIL_SHAPE.test(password.trim()) },
    { id: 'match', label: 'Both fields match', met: password.length > 0 && password === repeat },
  ];
}

export type StrengthLevel = 0 | 1 | 2 | 3 | 4;
export interface Strength { readonly level: StrengthLevel; readonly label: string }

const LABELS: Record<StrengthLevel, string> = { 0: 'Too short', 1: 'Weak', 2: 'Fair', 3: 'Good', 4: 'Strong' };

/**
 * A strength estimate from length and variety only (no dictionary): below the minimum it is too short; from there one
 * step for 12 characters, two for 16, three for 20, and one more for using at least three kinds of character (lower
 * case, upper case, digits, anything else). A password of few distinct characters ("aaaaaaaaaaaa") stays weak however
 * long it is. Length counts most, so a long passphrase of lower-case words rates well.
 */
export function passwordStrength(password: string): Strength {
  const length = characters(password);
  if (length < MIN_CHARACTERS) {
    return { level: 0, label: length === 0 ? '' : LABELS[0] };
  }
  if (new Set(password).size < 6) {
    return { level: 1, label: LABELS[1] };
  }
  const kinds = [/\p{Ll}/u, /\p{Lu}/u, /\p{Nd}/u, /[^\p{Ll}\p{Lu}\p{Nd}]/u].filter((kind) => kind.test(password)).length;
  const steps = (length >= 20 ? 3 : length >= 16 ? 2 : 1) + (kinds >= 3 ? 1 : 0);
  const level = Math.min(steps, 4) as StrengthLevel;
  return { level, label: LABELS[level] };
}
