/** What the replace endpoint accepts for one requirement (F7c, D-252): at most 12 terms of 1 to 40 characters. */
export const MAX_TERMS = 12;
export const MAX_TERM_LENGTH = 40;

/** The chips after an edit, and the one sentence to show when an entry was not taken. */
export interface TermsChange { readonly terms: string[]; readonly problem: string | null }

/** "  Code  of ethics " becomes "code of ethics": the form the server stores. */
export function normaliseTerm(raw: string): string {
  return raw.trim().replace(/\s+/g, ' ').toLowerCase();
}

/**
 * The chips after the control proposes `next`: the list it holds after a typed entry was added (Enter, a comma or
 * leaving the field), a pasted "a, b, c" was added, or a chip was removed. Every entry is split on commas and
 * normalised; empty entries and repeats are dropped quietly; an entry over 40 characters or beyond the twelfth is
 * not taken and says why. The server still has the last word: it drops terms that repeat the requirement's own words,
 * name a state or contain a digit (D-252), and the list it returns is what the grid then shows.
 */
export function applyTerms(next: readonly string[]): TermsChange {
  const terms: string[] = [];
  let problem: string | null = null;
  for (const entry of next.flatMap((value) => value.split(','))) {
    const term = normaliseTerm(entry);
    if (term === '' || terms.includes(term)) { continue; }
    if (term.length > MAX_TERM_LENGTH) {
      problem = `A search term has at most ${MAX_TERM_LENGTH} characters.`;
    } else if (terms.length >= MAX_TERMS) {
      problem = `A requirement has at most ${MAX_TERMS} search terms.`;
    } else {
      terms.push(term);
    }
  }
  return { terms, problem };
}

/** The same list in the same order. */
export function sameTerms(a: readonly string[], b: readonly string[]): boolean {
  return a.length === b.length && a.every((term, i) => term === b[i]);
}

export interface TermMark { readonly term: string; readonly changed: boolean }
/** Compare's "Search terms" aspect: each side in its own order, with the terms the other side lacks marked. */
export interface TermsDiff { readonly before: TermMark[]; readonly after: TermMark[] }

export function termsDiff(before: readonly string[], after: readonly string[]): TermsDiff {
  return {
    before: before.map((term) => ({ term, changed: !after.includes(term) })),
    after: after.map((term) => ({ term, changed: !before.includes(term) })),
  };
}

/** How many of the terms sent were not stored (the server's normaliser dropped them). */
export function droppedTerms(sent: readonly (readonly string[])[], stored: readonly (readonly string[])[]): number {
  return sent.reduce((dropped, terms, i) => dropped + terms.filter((t) => !(stored[i] ?? []).includes(t)).length, 0);
}
