import { describe, expect, it } from 'vitest';
import { PALETTE, TokenName, mix } from './tokens';

/** WCAG 2.1 relative luminance and contrast ratio. */
function luminance(hex: string): number {
  const [r, g, b] = [0, 1, 2].map((i) => {
    const c = parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16) / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

/** text: 4.5 (WCAG 1.4.3); mark: 3.0 for status marks, icons and the focus ring (1.4.11). */
type Use = 'text' | 'mark';
const MINIMUM: Record<Use, number> = { text: 4.5, mark: 3 };

/** Every background text or a mark sits on: the page, panels, hover and selected rows. */
const GROUNDS: TokenName[] = ['paper', 'surface', 'sunken', 'accentWash'];
/**
 * The DESIGN.md table's text and mark pairs. ink-3 is decorative (rules, disabled, the not-reviewed mark beside its
 * word) and line-strong a hairline, so neither is a text pair. line-control is the border that identifies a field, so it
 * is held to the 3:1 of WCAG 1.4.11 against the page and the field's own fill.
 */
const PAIRS: [TokenName, TokenName[], Use][] = [
  ['ink', GROUNDS, 'text'],
  ['ink2', GROUNDS, 'text'],
  ['accent', GROUNDS, 'text'],
  ['found', GROUNDS, 'text'],
  ['missing', GROUNDS, 'text'],
  // The primary button: paper on ink, at rest and under the pointer.
  ['paper', ['ink', 'inkHover'], 'text'],
  // The focus ring and the status marks against every ground.
  ['accent', GROUNDS, 'mark'],
  ['ink2', GROUNDS, 'mark'],
  ['lineControl', ['paper', 'surface'], 'mark'],
];

describe('design tokens', () => {
  for (const theme of ['light', 'dark'] as const) {
    for (const [fg, backgrounds, use] of PAIRS) {
      for (const bg of backgrounds) {
        it(`${fg} on ${bg} reaches ${MINIMUM[use]}:1 as ${use} in the ${theme} theme`, () => {
          expect(contrast(PALETTE[fg][theme], PALETTE[bg][theme])).toBeGreaterThanOrEqual(MINIMUM[use]);
        });
      }
    }
  }

  it('keeps the DESIGN.md table values', () => {
    expect(PALETTE.paper).toEqual({ light: '#FAFAF9', dark: '#0F1012' });
    expect(PALETTE.ink).toEqual({ light: '#141517', dark: '#ECECEE' });
    expect(PALETTE.accent).toEqual({ light: '#2F4FBF', dark: '#93A6F2' });
    expect(PALETTE.found).toEqual({ light: '#2D7A53', dark: '#62B88E' });
    expect(PALETTE.missing).toEqual({ light: '#B0372E', dark: '#E2806F' });
  });

  it('retires every v2 colour token', () => {
    const names = Object.keys(PALETTE);
    for (const retired of ['ground', 'surface2', 'raised', 'text', 'text2', 'text3', 'accentTint', 'ambiguous', 'neutral', 'seal', 'onAccent']) {
      expect(names).not.toContain(retired);
    }
  });

  it('mixes colours channel by channel', () => {
    expect(mix('#000000', '#FFFFFF', 0.5)).toBe('#808080');
    expect(mix('#2D7A53', '#FFFFFF', 0.1)).toBe('#EAF2EE');
  });
});
