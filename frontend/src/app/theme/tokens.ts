/**
 * The Lexvay colour tokens (docs/DESIGN.md v3 "Ledger", "Colour tokens") as light and dark hex pairs: the single source
 * the preset turns into light-dark() values (published as --p-lx-<kebab-name>) and the contrast test reads. No other
 * colour may appear in any stylesheet outside `theme/` (source-scan.spec.ts).
 */
export interface Pair { readonly light: string; readonly dark: string }

/** The DESIGN.md token table, value for value. */
const TABLE = {
  /** Page background. */
  paper: { light: '#FAFAF9', dark: '#0F1012' },
  /** Panels that must separate from paper: tables, side panes, inputs, overlays. */
  surface: { light: '#FFFFFF', dark: '#151618' },
  /** Hover rows, selected rows (with the rule), code wells. */
  sunken: { light: '#F3F3F1', dark: '#1B1C1F' },
  line: { light: '#E6E5E2', dark: '#26272B' },
  /** Input and secondary-button borders, column rules. */
  lineStrong: { light: '#D2D1CC', dark: '#34363B' },
  /** Primary text, and the primary button's fill. */
  ink: { light: '#141517', dark: '#ECECEE' },
  /** Secondary text, meta, table headers, icons. */
  ink2: { light: '#5A5D63', dark: '#A3A6AD' },
  /** Decorative only (rules, disabled, the not-reviewed mark's stroke); never text. */
  ink3: { light: '#8C8F95', dark: '#6E7178' },
  /** Links, the focus ring, the selection rule, the one active indicator. */
  accent: { light: '#2F4FBF', dark: '#93A6F2' },
  /** The selected row's background, always with the accent rule. */
  accentWash: { light: '#EEF1FA', dark: '#1C2133' },
  /** Evidence found, determined to meet. */
  found: { light: '#2D7A53', dark: '#62B88E' },
  /** Evidence missing, confirmed gap, determined not to meet, destructive. */
  missing: { light: '#B0372E', dark: '#E2806F' },
} as const satisfies Record<string, Pair>;

/** `amount` of `fg` over `bg` (0 to 1), in sRGB, rounded to a hex colour. */
export function mix(fg: string, bg: string, amount: number): string {
  const channel = (hex: string, i: number): number => parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16);
  return `#${[0, 1, 2].map((i) => Math.round(channel(fg, i) * amount + channel(bg, i) * (1 - amount)).toString(16).padStart(2, '0')).join('').toUpperCase()}`;
}

/**
 * The one shade the table implies but does not list: the primary button's hover and press, ink eased 14 % toward paper
 * (lighter in the light theme, darker in the dark one), so the fill answers the pointer without a second colour.
 */
const DERIVED = {
  inkHover: { light: mix(TABLE.ink.light, TABLE.paper.light, 0.86), dark: mix(TABLE.ink.dark, TABLE.paper.dark, 0.86) },
} satisfies Record<string, Pair>;

export const PALETTE = { ...TABLE, ...DERIVED };
export type TokenName = keyof typeof PALETTE;
