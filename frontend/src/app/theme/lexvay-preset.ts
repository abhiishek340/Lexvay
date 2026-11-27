import { definePreset } from '@primeuix/themes';
import auraBase from '@primeuix/themes/aura/base';
import auraBadge from '@primeuix/themes/aura/badge';
import auraButton from '@primeuix/themes/aura/button';
import auraPopover from '@primeuix/themes/aura/popover';
import { PALETTE, Pair } from './tokens';

/**
 * LexvayPreset v2 (docs/DESIGN.md v2). Every colour token is one light-dark() value (D-081), so a single
 * [data-theme] attribute drives PrimeNG and the application CSS alike; `extend.lx` publishes the same values as
 * --p-lx-* custom properties for application CSS.
 */
const ld = ({ light, dark }: Pair): string => `light-dark(${light}, ${dark})`;
type Tokens = { [K in keyof typeof PALETTE]: string };
const colours = Object.fromEntries(Object.entries(PALETTE).map(([name, pair]) => [name, ld(pair)])) as Tokens;

/**
 * Elevation (DESIGN.md "Elevation"). Light: soft shadows with a 1 px ring. Dark: no shadow, a 1 px ring in `line`
 * and an inner top highlight. Each layer carries both themes through light-dark(), so one value serves both.
 */
const ink = (alpha: number): string => `rgb(13 16 20 / ${alpha})`;
const ring = (alpha: number): string => `0 0 0 1px light-dark(${ink(alpha)}, ${PALETTE.line.dark})`;
const highlight = 'inset 0 1px 0 light-dark(transparent, rgb(255 255 255 / .04))';
const elevation = {
  e1: `0 1px 2px light-dark(${ink(0.05)}, transparent), ${ring(0.04)}, ${highlight}`,
  e2: `0 6px 16px -4px light-dark(${ink(0.08)}, transparent), ${ring(0.05)}, ${highlight}`,
  e3: `0 24px 48px -12px light-dark(${ink(0.18)}, transparent), ${ring(0.06)}, ${highlight}`,
};

/** The tokens as values, for the component token sets that chunks register (the `*-tokens.ts` files beside this one). */
export const lexvayTokens = { ...colours, ...elevation };
const t = lexvayTokens;

/** "surface2" is --p-lx-surface-2 and "accentTint" --p-lx-accent-tint, the names DESIGN.md uses. */
const cssName = (name: string): string => name.replace(/([a-z])([A-Z])/g, '$1-$2').replace(/([a-z]{2})(\d)/g, '$1-$2').toLowerCase();
const published = Object.fromEntries(Object.entries(lexvayTokens).map(([name, value]) => [cssName(name), value]));

/** Accent blue scale for the places PrimeNG asks for a shade (#2346E0 light, #7D9BFF dark). */
const accentScale = {
  50: '#EBEFFF', 100: '#D6DEFE', 200: '#AEBDFD', 300: '#7D9BFF', 400: '#5574F0', 500: '#2346E0',
  600: '#1C38B6', 700: '#172D8F', 800: '#11226B', 900: '#0C1849', 950: '#070F2E',
};

/**
 * Aura limited to what the shell paints first: buttons, the work queue badge and popovers (InfoTip, the StateMap card,
 * the shell's menus). Every other component's token set, Lexvay's overrides included, is registered by the chunk that
 * renders it (theme/component-tokens.ts and the `*-tokens.ts` files beside this one; docs/PERF.md, cut 2). The semantic
 * tokens below (form fields, lists, overlays, navigation) restyle every component, registered early or late.
 */
const Aura = {
  ...auraBase,
  components: { badge: auraBadge, button: auraButton, popover: auraPopover },
};

export const LexvayPreset = definePreset(Aura, {
  primitive: {
    borderRadius: { none: '0', xs: '4px', sm: '6px', md: '8px', lg: '12px', xl: '16px' },
  },
  semantic: {
    transitionDuration: '120ms',
    focusRing: { width: '2px', style: 'solid', color: t.accent, offset: '2px', shadow: 'none' },
    iconSize: '1rem',
    primary: { ...accentScale, color: t.accent, contrastColor: t.onAccent, hoverColor: t.accentHover, activeColor: t.accentActive },
    surface: {
      0: '#FFFFFF',
      50: ld({ light: '#F7F7F4', dark: '#EEF0F3' }),
      100: ld({ light: '#F1F1ED', dark: '#D9DDE2' }),
      200: ld({ light: '#E6E6E0', dark: '#A4ACB7' }),
      300: ld({ light: '#CFCFC8', dark: '#6F7783' }),
      400: ld({ light: '#8B919B', dark: '#59616C' }),
      500: ld({ light: '#6B727C', dark: '#3A414B' }),
      600: ld({ light: '#565D67', dark: '#252B33' }),
      700: ld({ light: '#3A4048', dark: '#1B2027' }),
      800: ld({ light: '#23272D', dark: '#171B21' }),
      900: ld({ light: '#15181C', dark: '#111419' }),
      950: ld({ light: '#0D1014', dark: '#0A0C0F' }),
    },
    text: { color: t.text, hoverColor: t.text, mutedColor: t.text2, hoverMutedColor: t.text },
    content: { borderRadius: '12px', background: t.surface, hoverBackground: t.surface2, borderColor: t.line, color: t.text, hoverColor: t.text },
    highlight: { background: t.accentTint, focusBackground: t.accentTint, color: t.text, focusColor: t.text },
    mask: { background: ld({ light: 'rgb(13 16 20 / .32)', dark: 'rgb(0 0 0 / .6)' }), color: t.text },
    formField: {
      paddingX: '0.75rem',
      paddingY: '0.5rem',
      background: t.surface,
      disabledBackground: t.surface2,
      filledBackground: t.surface,
      filledHoverBackground: t.surface,
      filledFocusBackground: t.surface,
      borderColor: t.lineStrong,
      hoverBorderColor: t.text2,
      focusBorderColor: t.accent,
      invalidBorderColor: t.missing,
      color: t.text,
      disabledColor: t.text2,
      // Placeholders are 14 px content, so they use text-2 (text-3 is for 18 px and up, D-303).
      placeholderColor: t.text2,
      invalidPlaceholderColor: t.missing,
      iconColor: t.text2,
      borderRadius: '8px',
      shadow: 'none',
      focusRing: { width: '2px', style: 'solid', color: t.accent, offset: '-1px', shadow: 'none' },
    },
    list: {
      padding: '0.375rem',
      gap: '2px',
      option: { padding: '0.5rem 0.75rem', borderRadius: '6px', focusBackground: t.surface2, color: t.text, focusColor: t.text, selectedBackground: t.accentTint, selectedFocusBackground: t.accentTint, selectedColor: t.text, selectedFocusColor: t.text },
      optionGroup: { padding: '0.5rem 0.75rem 0.25rem', fontWeight: '600', color: t.text2 },
    },
    overlay: {
      select: { borderRadius: '12px', background: t.raised, borderColor: 'transparent', color: t.text, shadow: t.e3 },
      popover: { borderRadius: '12px', padding: '1rem', background: t.raised, borderColor: 'transparent', color: t.text, shadow: t.e3 },
      modal: { borderRadius: '16px', padding: '1.5rem', background: t.raised, borderColor: 'transparent', color: t.text, shadow: t.e3 },
      navigation: { shadow: t.e3 },
    },
    navigation: {
      list: { padding: '0.375rem', gap: '2px' },
      item: { padding: '0.5rem 0.75rem', borderRadius: '6px', gap: '0.625rem', focusBackground: t.surface2, activeBackground: t.surface2, color: t.text, focusColor: t.text, activeColor: t.text, icon: { color: t.text2, focusColor: t.text, activeColor: t.text } },
      submenuLabel: { padding: '0.5rem 0.75rem 0.25rem', fontWeight: '600', color: t.text2 },
    },
  },
  components: {
    button: {
      root: {
        borderRadius: '8px', gap: '0.5rem', paddingX: '0.875rem', paddingY: '0.5rem',
        label: { fontWeight: '550' },
        sm: { fontSize: '0.8125rem', paddingX: '0.625rem', paddingY: '0.3125rem' },
        primary: { borderColor: t.accent, hoverBorderColor: t.accentHover, activeBorderColor: t.accentActive },
        secondary: {
          background: t.surface2, hoverBackground: t.line, activeBackground: t.line,
          borderColor: 'transparent', hoverBorderColor: 'transparent', activeBorderColor: 'transparent',
          color: t.text, hoverColor: t.text, activeColor: t.text,
          focusRing: { color: t.accent, shadow: 'none' },
        },
        danger: { background: t.missing, hoverBackground: t.missing, activeBackground: t.missing, borderColor: t.missing, hoverBorderColor: t.missing, activeBorderColor: t.missing, color: t.onAccent, hoverColor: t.onAccent, activeColor: t.onAccent },
      },
      outlined: {
        primary: { hoverBackground: t.accentTint, activeBackground: t.accentTint, borderColor: t.accent, color: t.accent },
        danger: { hoverBackground: t.missingTint, activeBackground: t.missingTint, borderColor: t.missing, color: t.missing },
        secondary: { hoverBackground: t.surface2, activeBackground: t.surface2, borderColor: t.lineStrong, color: t.text },
      },
      text: {
        primary: { hoverBackground: t.accentTint, activeBackground: t.accentTint, color: t.accent },
        secondary: { hoverBackground: t.surface2, activeBackground: t.line, color: t.text },
        danger: { hoverBackground: t.missingTint, activeBackground: t.missingTint, color: t.missing },
      },
      link: { color: t.accent, hoverColor: t.accentHover, activeColor: t.accentActive },
    },
    badge: {
      root: { borderRadius: '999px', padding: '0 0.4375rem', fontSize: '0.6875rem', fontWeight: '600', minWidth: '1.25rem', height: '1.25rem' },
      primary: { background: t.accent, color: t.onAccent },
      secondary: { background: t.surface2, color: t.text2 },
    },
    popover: {
      root: { gutter: '8px', borderColor: 'transparent' },
      content: { padding: '1rem' },
    },
  },
  extend: {
    lx: published,
  },
});
