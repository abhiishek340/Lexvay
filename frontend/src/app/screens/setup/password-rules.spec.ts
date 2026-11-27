import { describe, expect, it } from 'vitest';
import { RuleId, characters, passwordRules, passwordStrength } from './password-rules';

const met = (password: string, repeat = password) =>
  Object.fromEntries(passwordRules(password, repeat).map((r) => [r.id, r.met])) as Record<RuleId, boolean>;

describe('passwordRules', () => {
  it('lists the three rules in reading order, none met for an empty form', () => {
    expect(passwordRules('', '').map((r) => r.label)).toEqual(['12 to 128 characters', 'Not an email address', 'Both fields match']);
    expect(met('', '')).toEqual({ length: false, email: false, match: false });
  });

  it('counts characters as the server does: in code points, 12 to 128', () => {
    expect(met('a'.repeat(11)).length).toBe(false);
    expect(met('a'.repeat(12)).length).toBe(true);
    expect(met('a'.repeat(128)).length).toBe(true);
    expect(met('a'.repeat(129)).length).toBe(false);
    // Twelve four-byte characters are twelve characters (24 UTF-16 units), as on the server (D-402).
    const emoji = '\u{1F512}'.repeat(12);
    expect(characters(emoji)).toBe(12);
    expect(met(emoji).length).toBe(true);
    expect(met('\u{1F512}'.repeat(129)).length).toBe(false);
  });

  it('refuses anything shaped like an email address, which covers the account’s own', () => {
    expect(met('dana.whitfield@northfield.example').email).toBe(false);
    expect(met('  someone@uni.edu ').email).toBe(false);
    expect(met('at@sign but spaces').email).toBe(true);
    expect(met('correct horse battery staple').email).toBe(true);
  });

  it('needs both fields to hold the same password', () => {
    expect(met('correct horse battery', 'correct horse batter').match).toBe(false);
    expect(met('correct horse battery', 'correct horse battery').match).toBe(true);
  });
});

describe('passwordStrength', () => {
  const level = (password: string) => passwordStrength(password).level;

  it('says nothing for an empty field and "Too short" under twelve characters', () => {
    expect(passwordStrength('')).toEqual({ level: 0, label: '' });
    expect(passwordStrength('Ab1!Ab1!Ab1')).toEqual({ level: 0, label: 'Too short' });
  });

  it('rises with length: 12, 16 and 20 characters', () => {
    expect(passwordStrength('abcdefghijkl')).toEqual({ level: 1, label: 'Weak' });
    expect(passwordStrength('abcdefghijklmnop')).toEqual({ level: 2, label: 'Fair' });
    expect(passwordStrength('correct horse battery')).toEqual({ level: 3, label: 'Good' });
  });

  it('adds one step for three kinds of character, up to strong', () => {
    expect(level('Abcdefghijk1')).toBe(2);
    expect(level('Abcdefghijklmno1')).toBe(3);
    expect(passwordStrength('Correct horse battery 9')).toEqual({ level: 4, label: 'Strong' });
    expect(level('Correct horse battery staple 9 and more')).toBe(4);
  });

  it('keeps a password of few distinct characters weak, however long', () => {
    expect(level('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')).toBe(1);
    expect(level('abcabcabcabcabcabc12')).toBe(1);
  });

  it('tells upper from lower case in any alphabet, and counts spaces and symbols as the fourth kind', () => {
    expect(level('Ünïcödé pässwörd 12')).toBe(3);
    expect(level('mot de passe très long !')).toBe(3);
  });
});
