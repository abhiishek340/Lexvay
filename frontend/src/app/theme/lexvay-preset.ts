import { definePreset } from '@primeuix/themes';
import auraBase from '@primeuix/themes/aura/base';
import auraBadge from '@primeuix/themes/aura/badge';
import auraButton from '@primeuix/themes/aura/button';
import auraDatatable from '@primeuix/themes/aura/datatable';
import auraDialog from '@primeuix/themes/aura/dialog';
import auraIconfield from '@primeuix/themes/aura/iconfield';
import auraInputnumber from '@primeuix/themes/aura/inputnumber';
import auraInputtext from '@primeuix/themes/aura/inputtext';
import auraMenu from '@primeuix/themes/aura/menu';
import auraMessage from '@primeuix/themes/aura/message';
import auraPassword from '@primeuix/themes/aura/password';
import auraPopover from '@primeuix/themes/aura/popover';
import auraProgressbar from '@primeuix/themes/aura/progressbar';
import auraProgressspinner from '@primeuix/themes/aura/progressspinner';
import auraRadiobutton from '@primeuix/themes/aura/radiobutton';
import auraSelect from '@primeuix/themes/aura/select';
import auraSelectbutton from '@primeuix/themes/aura/selectbutton';
import auraSkeleton from '@primeuix/themes/aura/skeleton';
import auraSplitter from '@primeuix/themes/aura/splitter';
import auraTabs from '@primeuix/themes/aura/tabs';
import auraTag from '@primeuix/themes/aura/tag';
import auraTextarea from '@primeuix/themes/aura/textarea';
import auraToast from '@primeuix/themes/aura/toast';
import auraTogglebutton from '@primeuix/themes/aura/togglebutton';
import auraTooltip from '@primeuix/themes/aura/tooltip';
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

/** The tokens for lazy screens that register their own component token sets (screens/register/register-theme.ts). */
export const lexvayTokens = { ...colours, ...elevation };
const t = lexvayTokens;

/** "surface2" is --p-lx-surface-2 and "accentTint" --p-lx-accent-tint, the names DESIGN.md uses. */
const cssName = (name: string): string => name.replace(/([a-z])([A-Z])/g, '$1-$2').replace(/([a-z]{2})(\d)/g, '$1-$2').toLowerCase();
const published = Object.fromEntries(Object.entries(lexvayTokens).map(([name, value]) => [cssName(name), value]));

const severity = (fg: string, bg: string) => ({
  background: bg,
  borderColor: 'transparent',
  color: fg,
  detailColor: t.text,
  shadow: 'none',
  closeButton: { hoverBackground: t.surface2, focusRing: { color: t.accent, shadow: 'none' } },
  outlined: { color: fg, borderColor: fg },
  simple: { color: fg },
});
const toastSeverity = (fg: string) => ({ ...severity(fg, t.raised), detailColor: t.text2, shadow: t.e3 });

/** Accent blue scale for the places PrimeNG asks for a shade (#2346E0 light, #7D9BFF dark). */
const accentScale = {
  50: '#EBEFFF', 100: '#D6DEFE', 200: '#AEBDFD', 300: '#7D9BFF', 400: '#5574F0', 500: '#2346E0',
  600: '#1C38B6', 700: '#172D8F', 800: '#11226B', 900: '#0C1849', 950: '#070F2E',
};

/**
 * Aura limited to the components the shell and the screens' first paint render (D-082, PERF-FE-1). A component
 * only one lazy screen renders registers its token set from that screen's chunk (screens/rules/rules-theme.ts).
 */
const Aura = {
  ...auraBase,
  components: {
    badge: auraBadge, button: auraButton, datatable: auraDatatable, dialog: auraDialog, iconfield: auraIconfield, inputnumber: auraInputnumber,
    inputtext: auraInputtext, menu: auraMenu, message: auraMessage, password: auraPassword, popover: auraPopover, progressbar: auraProgressbar,
    progressspinner: auraProgressspinner, radiobutton: auraRadiobutton, select: auraSelect, selectbutton: auraSelectbutton, skeleton: auraSkeleton,
    splitter: auraSplitter, tabs: auraTabs, tag: auraTag, textarea: auraTextarea, toast: auraToast, togglebutton: auraTogglebutton, tooltip: auraTooltip,
  },
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
    tag: {
      root: { fontSize: '0.75rem', fontWeight: '550', padding: '0.125rem 0.625rem', gap: '0.375rem', borderRadius: '999px', roundedBorderRadius: '999px' },
      icon: { size: '0.875rem' },
      primary: { background: t.accentTint, color: t.accent },
      info: { background: t.accentTint, color: t.accent },
      secondary: { background: t.surface2, color: t.text2 },
      success: { background: t.foundTint, color: t.found },
      warn: { background: t.ambiguousTint, color: t.ambiguous },
      danger: { background: t.missingTint, color: t.missing },
      contrast: { background: t.text, color: t.surface },
    },
    badge: {
      root: { borderRadius: '999px', padding: '0 0.4375rem', fontSize: '0.6875rem', fontWeight: '600', minWidth: '1.25rem', height: '1.25rem' },
      primary: { background: t.accent, color: t.onAccent },
      secondary: { background: t.surface2, color: t.text2 },
    },
    message: {
      root: { borderRadius: '8px', borderWidth: '0' },
      content: { padding: '0.625rem 0.875rem', gap: '0.625rem' },
      info: severity(t.accent, t.accentTint),
      success: severity(t.found, t.foundTint),
      warn: severity(t.ambiguous, t.ambiguousTint),
      error: severity(t.missing, t.missingTint),
      secondary: severity(t.text2, t.surface2),
    },
    toast: {
      root: { width: '24rem', borderRadius: '12px', borderWidth: '0', blur: '0' },
      content: { padding: '0', gap: '0.625rem' },
      info: toastSeverity(t.accent),
      success: toastSeverity(t.found),
      warn: toastSeverity(t.ambiguous),
      error: toastSeverity(t.missing),
    },
    datatable: {
      headerCell: { background: 'transparent', color: t.text2, borderColor: t.line, padding: '0.75rem 1rem' },
      columnTitle: { fontWeight: '600' },
      row: { background: 'transparent', hoverBackground: t.surface2, color: t.text, selectedBackground: t.accentTint, selectedColor: t.text },
      // 16 px above and below a 20 px line: the 52 px row of DESIGN.md.
      bodyCell: { borderColor: t.line, padding: '1rem 1rem' },
    },
    tabs: {
      tablist: { background: 'transparent', borderColor: t.line },
      tab: { color: t.text2, hoverColor: t.text, activeColor: t.text, padding: '0.75rem 1rem', fontWeight: '550', gap: '0.5rem' },
      tabpanel: { background: 'transparent', padding: '0' },
      activeBar: { height: '2px', background: t.accent },
      navButton: { background: t.ground },
    },
    dialog: {
      header: { padding: '1.5rem 1.5rem 0.75rem', gap: '0.5rem' },
      title: { fontSize: '1.125rem', fontWeight: '620' },
    },
    popover: {
      root: { gutter: '8px', borderColor: 'transparent' },
      content: { padding: '1rem' },
    },
    menu: {
      root: { background: t.raised, borderColor: 'transparent', borderRadius: '12px', shadow: t.e3 },
      separator: { borderColor: t.line },
    },
    tooltip: {
      root: { background: t.text, color: t.surface, borderRadius: '6px', padding: '0.375rem 0.625rem', fontSize: '0.8125rem', maxWidth: '16rem', shadow: t.e2 },
    },
    splitter: {
      root: { background: 'transparent', borderColor: t.line },
      gutter: { background: t.line },
      handle: { size: '2.5rem', background: t.text2 },
    },
    selectbutton: {
      root: { borderRadius: '10px' },
    },
    togglebutton: {
      // A segmented control: a quiet track, the chosen segment raised onto it.
      root: {
        padding: '0.1875rem', borderRadius: '10px', gap: '0.375rem', fontWeight: '550',
        background: t.surface2, checkedBackground: t.surface2, hoverBackground: t.surface2,
        borderColor: t.surface2, checkedBorderColor: t.surface2,
        color: t.text2, hoverColor: t.text, checkedColor: t.text,
      },
      content: { padding: '0.25rem 0.75rem', borderRadius: '7px', checkedBackground: t.raised, checkedShadow: t.e1 },
      icon: { color: t.text2, hoverColor: t.text, checkedColor: t.text },
    },
    progressbar: {
      root: { background: t.surface2, borderRadius: '999px', height: '6px' },
      value: { background: t.accent },
    },
    radiobutton: {
      root: { borderColor: t.lineStrong, hoverBorderColor: t.text2, checkedBorderColor: t.accent, checkedHoverBorderColor: t.accentHover, checkedBackground: t.accent, checkedHoverBackground: t.accentHover },
      icon: { checkedColor: t.onAccent, checkedHoverColor: t.onAccent },
    },
    skeleton: {
      root: { borderRadius: '8px', background: t.surface2, animationBackground: ld({ light: 'rgb(255 255 255 / .6)', dark: 'rgb(255 255 255 / .04)' }) },
    },
  },
  extend: {
    lx: published,
  },
});
