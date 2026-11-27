/**
 * How well `query` matches `text` as a subsequence, the command palette's only matching rule: 0 when the query's
 * characters (spaces ignored, case ignored) do not all appear in order. Otherwise each matched character is worth 1,
 * plus 8 at the start of a word, 5 right after the previous match and 4 more at the very start; a shorter text wins a
 * tie. The best placement is found exactly (dynamic programming), so "ca" scores California's word start, not the
 * "ca" inside "Medical".
 */
export function fuzzyScore(query: string, text: string): number {
  const q = query.toLowerCase().replace(/\s+/g, '');
  const t = text.toLowerCase();
  if (q.length === 0) { return 0; }
  if (q.length > t.length) { return 0; }
  const NONE = -Infinity;
  // best[j]: the best score of the query prefix so far ending with a match at text position j.
  let best = new Array<number>(t.length).fill(NONE);
  for (let i = 0; i < q.length; i++) {
    const next = new Array<number>(t.length).fill(NONE);
    let bestBefore = NONE;
    for (let j = 0; j < t.length; j++) {
      if (i > 0 && j > 0) { bestBefore = Math.max(bestBefore, best[j - 1]); }
      if (t[j] !== q[i]) { continue; }
      const wordStart = j === 0 || /[^a-z0-9]/.test(t[j - 1]) || (text[j] !== t[j] && text[j - 1] === t[j - 1]);
      const gain = 1 + (wordStart ? 8 : 0) + (j === 0 ? 4 : 0);
      if (i === 0) {
        next[j] = gain;
      } else {
        const consecutive = j > 0 && best[j - 1] !== NONE ? best[j - 1] + gain + 5 : NONE;
        const apart = bestBefore !== NONE ? bestBefore + gain : NONE;
        next[j] = Math.max(consecutive, apart);
      }
    }
    best = next;
  }
  const score = Math.max(...best);
  return score === NONE ? 0 : score - t.length / 1000;
}

/** The better of the label's score and, slightly behind it, the best keyword's (state codes, requirement refs). */
export function commandScore(query: string, label: string, keywords: readonly string[]): number {
  const fromKeywords = Math.max(0, ...keywords.map((k) => fuzzyScore(query, k) - 0.5));
  return Math.max(fuzzyScore(query, label), fromKeywords);
}
