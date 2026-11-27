/**
 * The Lexvay colour tokens (docs/DESIGN.md v2, "Tokens") as light and dark hex pairs: the single source the preset
 * turns into light-dark() values (published as --p-lx-<kebab-name>) and the contrast test reads.
 */
export interface Pair { readonly light: string; readonly dark: string }

/** The DESIGN.md token table, value for value. */
const TABLE = {
  ground: { light: '#F7F7F4', dark: '#0A0C0F' },
  surface: { light: '#FFFFFF', dark: '#111419' },
  surface2: { light: '#F1F1ED', dark: '#171B21' },
  raised: { light: '#FFFFFF', dark: '#1B2027' },
  line: { light: '#E6E6E0', dark: '#252B33' },
  text: { light: '#0D1014', dark: '#EEF0F3' },
  text2: { light: '#565D67', dark: '#A4ACB7' },
  /** Tertiary text: 18 px and larger only, on surface or raised (3:1). Never placeholders (they are 14 px). */
  text3: { light: '#8B919B', dark: '#6F7783' },
  accent: { light: '#2346E0', dark: '#7D9BFF' },
  accentTint: { light: '#EBEFFF', dark: '#1A2347' },
  found: { light: '#0B7A52', dark: '#45D19E' },
  ambiguous: { light: '#A35C00', dark: '#F3B54A' },
  missing: { light: '#C2362B', dark: '#FF8B7D' },
  /** A fill only (not reviewed, not a target): below 3:1, so never text or an icon. */
  neutral: { light: '#9AA1AB', dark: '#59616C' },
  seal: { light: '#1C3A8F', dark: '#9CB4FF' },
} as const satisfies Record<string, Pair>;

/** `amount` of `fg` over `bg` (0 to 1), in sRGB, rounded to a hex colour. */
export function mix(fg: string, bg: string, amount: number): string {
  const channel = (hex: string, i: number): number => parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16);
  return `#${[0, 1, 2].map((i) => Math.round(channel(fg, i) * amount + channel(bg, i) * (1 - amount)).toString(16).padStart(2, '0')).join('').toUpperCase()}`;
}

const over = (fg: Pair, bg: Pair, light: number, dark: number): Pair => ({ light: mix(fg.light, bg.light, light), dark: mix(fg.dark, bg.dark, dark) });
const BLACK: Pair = { light: '#000000', dark: '#000000' };

/**
 * Tokens the table implies but does not list: the boundary of form controls (WCAG 1.4.11 needs 3:1 against
 * every background a control sits on; `line` is for hairlines and is far lighter), the text on an accent or
 * status fill, the accent's hover and press shades ("buttons darken 4 %"), the status tints (10 % light,
 * 16 % dark over surface) and the darker in-review fill of a StateMap tile.
 */
const DERIVED = {
  lineStrong: { light: '#80868F', dark: '#6F7783' },
  onAccent: { light: '#FFFFFF', dark: TABLE.ground.dark },
  accentHover: over(BLACK, TABLE.accent, 0.04, 0.04),
  accentActive: over(BLACK, TABLE.accent, 0.08, 0.08),
  foundTint: over(TABLE.found, TABLE.surface, 0.1, 0.16),
  ambiguousTint: over(TABLE.ambiguous, TABLE.surface, 0.1, 0.16),
  missingTint: over(TABLE.missing, TABLE.surface, 0.1, 0.16),
  accentFill: over(TABLE.accent, TABLE.surface, 0.26, 0.24),
} satisfies Record<string, Pair>;

export const PALETTE = { ...TABLE, ...DERIVED };
export type TokenName = keyof typeof PALETTE;
