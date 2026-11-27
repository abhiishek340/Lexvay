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

/** text: 4.5 (WCAG 1.4.3); large: 3.0 (18 px and up); ui: 3.0 for icons, control boundaries and focus (1.4.11). */
type Use = 'text' | 'large' | 'ui';
const MINIMUM: Record<Use, number> = { text: 4.5, large: 3, ui: 3 };

const PAGES: TokenName[] = ['ground', 'surface', 'surface2', 'raised'];
const PAIRS: [TokenName, TokenName[], Use][] = [
  ['text', [...PAGES, 'accentTint', 'foundTint', 'ambiguousTint', 'missingTint'], 'text'],
  ['text2', [...PAGES, 'accentTint'], 'text'],
  ['text3', ['surface', 'raised'], 'large'],
  ['accent', [...PAGES, 'accentTint', 'accentFill'], 'text'],
  ['onAccent', ['accent', 'accentHover', 'accentActive', 'found', 'missing'], 'text'],
  ['found', [...PAGES, 'foundTint'], 'text'],
  ['ambiguous', [...PAGES, 'ambiguousTint'], 'text'],
  ['missing', [...PAGES, 'missingTint'], 'text'],
  ['seal', [...PAGES], 'text'],
  ['lineStrong', [...PAGES], 'ui'],
];

describe('design tokens', () => {
  for (const theme of ['light', 'dark'] as const) {
    for (const [fg, backgrounds, use] of PAIRS) {
      for (const bg of backgrounds) {
        it(`${fg} on ${bg} reaches ${MINIMUM[use]}:1 in the ${theme} theme`, () => {
          expect(contrast(PALETTE[fg][theme], PALETTE[bg][theme])).toBeGreaterThanOrEqual(MINIMUM[use]);
        });
      }
    }
  }

  it('keeps the DESIGN.md table values', () => {
    expect(PALETTE.accent).toEqual({ light: '#2346E0', dark: '#7D9BFF' });
    expect(PALETTE.ground).toEqual({ light: '#F7F7F4', dark: '#0A0C0F' });
  });

  it('mixes colours channel by channel', () => {
    expect(mix('#000000', '#FFFFFF', 0.5)).toBe('#808080');
    expect(mix('#0B7A52', '#FFFFFF', 0.1)).toBe('#E7F2EE');
  });
});
