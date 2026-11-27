import { definePreset } from '@primeuix/themes';
import auraBase from '@primeuix/themes/aura/base';
import auraButton from '@primeuix/themes/aura/button';
import auraPopover from '@primeuix/themes/aura/popover';
import auraTooltip from '@primeuix/themes/aura/tooltip';
import { PALETTE, Pair } from './tokens';

/**
 * LexvayPreset v3 "Ledger" (docs/DESIGN.md v3). Every colour token is one light-dark() value (D-081), so a single
 * [data-theme] attribute drives PrimeNG and the application CSS alike; `extend.lx` publishes the same values as
 * --p-lx-* custom properties for application CSS. Ink on paper: the primary colour is ink, the accent is kept for
 * links, focus and selection.
 */
const ld = ({ light, dark }: Pair): string => `light-dark(${light}, ${dark})`;
type Tokens = { [K in keyof typeof PALETTE]: string };
const colours = Object.fromEntries(Object.entries(PALETTE).map(([name, pair]) => [name, ld(pair)])) as Tokens;

/**
 * The one elevation (DESIGN.md "Elevation"), for overlays only: menus, popovers, dialogs, toasts, the report sheet.
 * Page content has none. Both themes in one value through light-dark().
 */
const overlay = `0 12px 32px -8px light-dark(rgb(20 21 23 / .14), rgb(0 0 0 / .5)), 0 0 0 1px ${colours.line}`;

/** The tokens as values, for the component token sets that chunks register (the `*-tokens.ts` files beside this one). */
export const lexvayTokens = { ...colours, overlay };
const t = lexvayTokens;

/** "ink2" is --p-lx-ink-2, "lineStrong" --p-lx-line-strong and "accentWash" --p-lx-accent-wash, the names DESIGN.md uses. */
const cssName = (name: string): string => name.replace(/([a-z])([A-Z])/g, '$1-$2').replace(/([a-z])(\d)/g, '$1-$2').toLowerCase();
const published = Object.fromEntries(Object.entries(lexvayTokens).map(([name, value]) => [cssName(name), value]));

/**
 * The neutral ramp PrimeNG reads where Aura names a shade. Aura pairs a light shade with its mirror in the dark theme
 * (light-dark({surface.0}, {surface.950}) for a field's background), so each step carries the light theme's value
 * for that step and the dark theme's value for its mirror, both taken from the table where the table has one.
 */
const step = (light: string, dark: string): string => ld({ light, dark });
const P = PALETTE;
const surfaceScale = {
  0: step(P.surface.light, P.ink.dark),
  50: step(P.paper.light, P.ink.dark),
  100: step(P.sunken.light, '#D4D5D8'),
  200: step(P.line.light, P.ink2.dark),
  300: step(P.lineStrong.light, P.ink3.dark),
  400: step(P.ink3.light, '#4A4C52'),
  500: step('#73767C', P.lineStrong.dark),
  600: step(P.ink2.light, '#2C2E32'),
  700: step('#3A3C40', P.line.dark),
  800: step('#26272B', P.sunken.dark),
  900: step('#1B1C1F', P.surface.dark),
  950: step(P.ink.light, P.paper.dark),
};

/**
 * Aura limited to what the shell paints first: buttons, popovers (Help, the shell's menus) and tooltips (the rail's
 * collapsed names, Demo data). Every other component's token set is registered by the chunk that renders it
 * (theme/component-tokens.ts and the `*-tokens.ts` files beside this one; docs/PERF.md, cut 2). The semantic tokens
 * below (form fields, lists, overlays, navigation) restyle every component, registered early or late.
 */
const Aura = {
  ...auraBase,
  components: { button: auraButton, popover: auraPopover, tooltip: auraTooltip },
};

/** The secondary button: surface with a 1 px line-strong border, sunken on hover (DESIGN.md "Buttons"). */
const secondary = (color: string) => ({
  background: t.surface, hoverBackground: t.sunken, activeBackground: t.sunken,
  borderColor: t.lineStrong, hoverBorderColor: t.lineStrong, activeBorderColor: t.lineStrong,
  color, hoverColor: color, activeColor: color,
  focusRing: { color: t.accent, shadow: 'none' },
});
/** The ghost button: text only, sunken on hover. */
const ghost = (color: string) => ({ hoverBackground: t.sunken, activeBackground: t.sunken, color });

export const LexvayPreset = definePreset(Aura, {
  primitive: {
    borderRadius: { none: '0', xs: '2px', sm: '4px', md: '6px', lg: '8px', xl: '8px' },
  },
  semantic: {
    typography: { fontFamily: 'inherit', fontWeight: '400', fontSize: '0.8125rem', lineHeight: '1.25rem' },
    transitionDuration: '120ms',
    focusRing: { width: '2px', style: 'solid', color: t.accent, offset: '2px', shadow: 'none' },
    iconSize: '1rem',
    primary: {
      50: t.sunken, 100: t.line, 200: t.lineStrong, 300: t.ink3, 400: t.ink2, 500: t.ink, 600: t.ink, 700: t.ink, 800: t.ink, 900: t.ink, 950: t.ink,
      color: t.ink, contrastColor: t.paper, hoverColor: t.inkHover, activeColor: t.inkHover,
    },
    surface: surfaceScale,
    text: { color: t.ink, hoverColor: t.ink, mutedColor: t.ink2, hoverMutedColor: t.ink },
    content: { borderRadius: '6px', background: t.surface, hoverBackground: t.sunken, borderColor: t.line, color: t.ink, hoverColor: t.ink },
    highlight: { background: t.accentWash, focusBackground: t.accentWash, color: t.ink, focusColor: t.ink },
    mask: { background: 'light-dark(rgb(20 21 23 / .32), rgb(0 0 0 / .6))', color: t.ink },
    formField: {
      fontSize: '0.8125rem',
      paddingX: '0.75rem',
      paddingY: '0.3125rem',
      sm: { fontSize: '0.8125rem', paddingX: '0.75rem', paddingY: '0.3125rem' },
      lg: { fontSize: '0.8125rem', paddingX: '0.75rem', paddingY: '0.3125rem' },
      background: t.surface,
      disabledBackground: t.sunken,
      filledBackground: t.surface,
      filledHoverBackground: t.surface,
      filledFocusBackground: t.surface,
      borderColor: t.lineStrong,
      hoverBorderColor: t.ink3,
      focusBorderColor: t.accent,
      invalidBorderColor: t.missing,
      color: t.ink,
      disabledColor: t.ink2,
      placeholderColor: t.ink2,
      invalidPlaceholderColor: t.missing,
      floatLabelColor: t.ink2,
      floatLabelFocusColor: t.ink2,
      floatLabelActiveColor: t.ink2,
      iconColor: t.ink2,
      borderRadius: '6px',
      shadow: 'none',
      focusRing: { width: '1px', style: 'solid', color: t.accent, offset: '0', shadow: 'none' },
    },
    list: {
      padding: '0.25rem',
      gap: '0',
      header: { padding: '0.5rem 0.75rem 0.25rem' },
      option: {
        padding: '0.375rem 0.5rem', borderRadius: '4px', focusBackground: t.sunken, color: t.ink, focusColor: t.ink,
        selectedBackground: t.accentWash, selectedFocusBackground: t.accentWash, selectedColor: t.ink, selectedFocusColor: t.ink,
        icon: { color: t.ink2, focusColor: t.ink },
      },
      optionGroup: { padding: '0.5rem 0.5rem 0.25rem', fontWeight: '500', color: t.ink2 },
    },
    overlay: {
      select: { borderRadius: '8px', background: t.surface, borderColor: 'transparent', color: t.ink, shadow: overlay },
      popover: { borderRadius: '8px', padding: '0.75rem', background: t.surface, borderColor: 'transparent', color: t.ink, shadow: overlay },
      modal: { borderRadius: '8px', padding: '1.5rem', background: t.surface, borderColor: 'transparent', color: t.ink, shadow: overlay },
      navigation: { shadow: overlay },
    },
    navigation: {
      list: { padding: '0.25rem', gap: '0' },
      item: {
        padding: '0.375rem 0.5rem', borderRadius: '4px', gap: '0.5rem', focusBackground: t.sunken, activeBackground: t.sunken,
        color: t.ink, focusColor: t.ink, activeColor: t.ink, icon: { color: t.ink2, focusColor: t.ink, activeColor: t.ink },
      },
      submenuLabel: { padding: '0.5rem 0.5rem 0.25rem', fontWeight: '500', color: t.ink2 },
      submenuIcon: { color: t.ink2, focusColor: t.ink, activeColor: t.ink },
    },
  },
  components: {
    button: {
      root: {
        borderRadius: '6px', gap: '0.5rem', paddingX: '0.75rem', paddingY: '0.3125rem', iconOnlyWidth: '2rem', fontSize: '0.8125rem',
        label: { fontWeight: '500' },
        sm: { fontSize: '0.8125rem', paddingX: '0.75rem', paddingY: '0.3125rem', iconOnlyWidth: '2rem' },
        lg: { fontSize: '0.8125rem', paddingX: '0.875rem', paddingY: '0.4375rem', iconOnlyWidth: '2.25rem' },
        primary: {
          background: t.ink, hoverBackground: t.inkHover, activeBackground: t.inkHover,
          borderColor: t.ink, hoverBorderColor: t.inkHover, activeBorderColor: t.inkHover,
          color: t.paper, hoverColor: t.paper, activeColor: t.paper,
          focusRing: { color: t.accent, shadow: 'none' },
        },
        secondary: secondary(t.ink),
        danger: secondary(t.missing),
      },
      outlined: {
        primary: { hoverBackground: t.sunken, activeBackground: t.sunken, borderColor: t.lineStrong, color: t.ink },
        secondary: { hoverBackground: t.sunken, activeBackground: t.sunken, borderColor: t.lineStrong, color: t.ink },
        danger: { hoverBackground: t.sunken, activeBackground: t.sunken, borderColor: t.lineStrong, color: t.missing },
      },
      text: { primary: ghost(t.ink), secondary: ghost(t.ink), danger: ghost(t.missing) },
      link: { color: t.accent, hoverColor: t.accent, activeColor: t.accent },
    },
    popover: {
      root: { gutter: '6px', borderColor: 'transparent' },
      content: { padding: '0.75rem' },
    },
    tooltip: {
      root: { background: t.ink, color: t.paper, borderRadius: '6px', padding: '0.25rem 0.5rem', fontSize: '0.75rem', maxWidth: '20rem', shadow: 'none', gutter: '6px' },
    },
  },
  extend: {
    lx: published,
  },
});
