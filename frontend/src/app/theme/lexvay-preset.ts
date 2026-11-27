import { definePreset } from '@primeuix/themes';
import auraAccordion from '@primeuix/themes/aura/accordion';
import auraBase from '@primeuix/themes/aura/base';
import auraBadge from '@primeuix/themes/aura/badge';
import auraButton from '@primeuix/themes/aura/button';
import auraDatatable from '@primeuix/themes/aura/datatable';
import auraDatepicker from '@primeuix/themes/aura/datepicker';
import auraDialog from '@primeuix/themes/aura/dialog';
import auraIconfield from '@primeuix/themes/aura/iconfield';
import auraInputnumber from '@primeuix/themes/aura/inputnumber';
import auraInputtext from '@primeuix/themes/aura/inputtext';
import auraListbox from '@primeuix/themes/aura/listbox';
import auraMenu from '@primeuix/themes/aura/menu';
import auraMessage from '@primeuix/themes/aura/message';
import auraPassword from '@primeuix/themes/aura/password';
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

/**
 * The Lexvay design tokens (docs/DESIGN.md, "Tokens"), written once here as light-dark() pairs.
 * PrimeNG resolves light-dark() through the color-scheme it sets for [data-theme="dark"], and the
 * `extend.lx` group below publishes the same values as CSS variables (--p-lx-*) for application CSS.
 */
const ld = (light: string, dark: string): string => `light-dark(${light}, ${dark})`;

const t = {
  ground: ld('#F7F7F5', '#0F1113'),
  card: ld('#FFFFFF', '#16191C'),
  raised: ld('#FFFFFF', '#1C2024'),
  sunken: ld('#F0F0EC', '#121518'),
  hover: ld('#F2F2EE', '#1E2226'),
  hairline: ld('#DCDCD6', '#2A2F35'),
  /** Boundary of form controls: 3:1 against the card in both themes (WCAG 1.4.11). */
  control: ld('#8B8D86', '#6B727A'),
  text: ld('#15171A', '#ECEDEE'),
  textSecondary: ld('#555B63', '#A3AAB3'),
  primary: ld('#1B3A6B', '#9DB8E8'),
  primaryHover: ld('#142C52', '#B6CBEE'),
  primaryActive: ld('#0F2240', '#C9D8F2'),
  onPrimary: ld('#FFFFFF', '#0F1113'),
  selected: ld('#E7EDF6', '#1D2A3D'),
  selectedText: ld('#14305A', '#DCE5F3'),
  found: ld('#1D6B3A', '#7CCF98'),
  foundBg: ld('#E9F3EC', '#14261B'),
  foundBorder: ld('#A9CFB6', '#2C5A3B'),
  ambiguous: ld('#8A5A00', '#E8B45A'),
  ambiguousBg: ld('#FAF1DE', '#2B220F'),
  ambiguousBorder: ld('#DFC48A', '#5E4719'),
  missing: ld('#9B2C1F', '#F0958A'),
  missingBg: ld('#F9EAE7', '#2E1714'),
  missingBorder: ld('#E0AFA7', '#663129'),
  noticeBg: ld('#FBF3DC', '#2E2609'),
  noticeFg: ld('#5A3E00', '#F3DFA0'),
  shadow: '0 8px 24px rgba(15, 17, 19, 0.14), 0 1px 3px rgba(15, 17, 19, 0.08)',
};

const severity = (fg: string, bg: string, border: string) => ({
  background: bg,
  borderColor: border,
  color: fg,
  detailColor: t.text,
  shadow: 'none',
  closeButton: { hoverBackground: t.hover, focusRing: { color: t.primary, shadow: 'none' } },
  outlined: { color: fg, borderColor: fg },
  simple: { color: fg },
});

/** Ink blue scale around the primary (#1B3A6B light, #9DB8E8 dark), used where PrimeNG asks for a shade. */
const ink = {
  50: '#EEF2F9', 100: '#DCE5F3', 200: '#B9CBE7', 300: '#9DB8E8', 400: '#6F8FC4', 500: '#3F6299',
  600: '#1B3A6B', 700: '#152E56', 800: '#102342', 900: '#0B182E', 950: '#060E1C',
};


/**
 * Aura limited to the components Lexvay renders. The full Aura preset carries token sets for about 90
 * components (130 kB of the initial bundle); PrimeNG needs tokens only for components on the page.
 * A component added to a template must be added here too, or it renders without its theme.
 */
const Aura = {
  ...auraBase,
  components: { accordion: auraAccordion, badge: auraBadge, button: auraButton, datatable: auraDatatable, datepicker: auraDatepicker, dialog: auraDialog, iconfield: auraIconfield, inputnumber: auraInputnumber, inputtext: auraInputtext, listbox: auraListbox, menu: auraMenu, message: auraMessage, password: auraPassword, progressbar: auraProgressbar, progressspinner: auraProgressspinner, radiobutton: auraRadiobutton, select: auraSelect, selectbutton: auraSelectbutton, skeleton: auraSkeleton, splitter: auraSplitter, tabs: auraTabs, tag: auraTag, textarea: auraTextarea, toast: auraToast, togglebutton: auraTogglebutton, tooltip: auraTooltip },
};

export const LexvayPreset = definePreset(Aura, {
  primitive: {
    borderRadius: { none: '0', xs: '2px', sm: '4px', md: '4px', lg: '4px', xl: '4px' },
  },
  semantic: {
    focusRing: { width: '2px', style: 'solid', color: t.primary, offset: '2px', shadow: 'none' },
    iconSize: '1rem',
    primary: {
      ...ink,
      color: t.primary,
      contrastColor: t.onPrimary,
      hoverColor: t.primaryHover,
      activeColor: t.primaryActive,
    },
    surface: {
      0: '#FFFFFF',
      50: ld('#F7F7F5', '#ECEDEE'),
      100: ld('#EFEFEB', '#E6E8EA'),
      200: ld('#DCDCD6', '#DADDE0'),
      300: ld('#C8C8C1', '#C4C9CE'),
      400: ld('#9C9E99', '#A3AAB3'),
      500: ld('#6B7077', '#6B727A'),
      600: ld('#555B63', '#3A4047'),
      700: ld('#3A3F45', '#2A2F35'),
      800: ld('#24282C', '#1C2024'),
      900: ld('#15171A', '#16191C'),
      950: ld('#0B0C0E', '#0F1113'),
    },
    text: { color: t.text, hoverColor: t.text, mutedColor: t.textSecondary, hoverMutedColor: t.text },
    content: { borderRadius: '4px', background: t.card, hoverBackground: t.hover, borderColor: t.hairline, color: t.text, hoverColor: t.text },
    highlight: { background: t.selected, focusBackground: t.selected, color: t.selectedText, focusColor: t.selectedText },
    mask: { background: ld('rgba(21, 23, 26, 0.36)', 'rgba(0, 0, 0, 0.6)'), color: t.text },
    formField: {
      background: t.card,
      disabledBackground: t.sunken,
      filledBackground: t.card,
      filledHoverBackground: t.card,
      filledFocusBackground: t.card,
      borderColor: t.control,
      hoverBorderColor: t.text,
      focusBorderColor: t.primary,
      invalidBorderColor: t.missing,
      color: t.text,
      disabledColor: t.textSecondary,
      placeholderColor: t.textSecondary,
      invalidPlaceholderColor: t.missing,
      iconColor: t.textSecondary,
      borderRadius: '4px',
      shadow: 'none',
      focusRing: { width: '2px', style: 'solid', color: t.primary, offset: '-1px', shadow: 'none' },
    },
    overlay: {
      select: { borderRadius: '4px', background: t.raised, borderColor: t.hairline, color: t.text, shadow: t.shadow },
      popover: { borderRadius: '4px', padding: '0.75rem', background: t.raised, borderColor: t.hairline, color: t.text, shadow: t.shadow },
      modal: { borderRadius: '4px', padding: '1.5rem', background: t.raised, borderColor: t.hairline, color: t.text, shadow: t.shadow },
      navigation: { shadow: t.shadow },
    },
    list: { option: { focusBackground: t.hover, color: t.text, focusColor: t.text } },
    navigation: { item: { focusBackground: t.hover, activeBackground: t.hover, color: t.text, focusColor: t.text, activeColor: t.text } },
  },
  components: {
    button: {
      root: {
        label: { fontWeight: '500' }, paddingY: '0.4375rem', paddingX: '0.875rem',
        secondary: {
          background: t.card, hoverBackground: t.hover, activeBackground: t.sunken,
          borderColor: t.control, hoverBorderColor: t.text, activeBorderColor: t.text,
          color: t.text, hoverColor: t.text, activeColor: t.text,
          focusRing: { color: t.primary, shadow: 'none' },
        },
      },
      outlined: {
        primary: { hoverBackground: t.selected, activeBackground: t.selected, borderColor: t.primary, color: t.primary },
        danger: { hoverBackground: t.missingBg, activeBackground: t.missingBg, borderColor: t.missing, color: t.missing },
        secondary: { hoverBackground: t.hover, activeBackground: t.sunken, borderColor: t.control, color: t.text },
      },
      text: {
        primary: { hoverBackground: t.selected, activeBackground: t.selected, color: t.primary },
        secondary: { hoverBackground: t.hover, activeBackground: t.sunken, color: t.text },
        danger: { hoverBackground: t.missingBg, activeBackground: t.missingBg, color: t.missing },
      },
      link: { color: t.primary, hoverColor: t.primaryHover, activeColor: t.primaryActive },
    },
    tag: {
      root: { fontSize: '0.75rem', fontWeight: '500', padding: '0.125rem 0.5rem', gap: '0.375rem', borderRadius: '2px' },
      primary: { background: t.selected, color: t.selectedText },
      info: { background: t.selected, color: t.selectedText },
      secondary: { background: t.sunken, color: t.textSecondary },
      success: { background: t.foundBg, color: t.found },
      warn: { background: t.ambiguousBg, color: t.ambiguous },
      danger: { background: t.missingBg, color: t.missing },
    },
    message: {
      info: severity(t.primary, t.selected, t.hairline),
      success: severity(t.found, t.foundBg, t.foundBorder),
      warn: severity(t.ambiguous, t.ambiguousBg, t.ambiguousBorder),
      error: severity(t.missing, t.missingBg, t.missingBorder),
      secondary: severity(t.textSecondary, t.sunken, t.hairline),
    },
    toast: {
      root: { width: '24rem', borderRadius: '4px', blur: '0' },
      info: { ...severity(t.primary, t.raised, t.hairline), shadow: t.shadow },
      success: { ...severity(t.found, t.raised, t.foundBorder), shadow: t.shadow },
      warn: { ...severity(t.ambiguous, t.raised, t.ambiguousBorder), shadow: t.shadow },
      error: { ...severity(t.missing, t.raised, t.missingBorder), shadow: t.shadow },
    },
    datatable: {
      headerCell: { background: 'transparent', color: t.textSecondary, borderColor: t.hairline, padding: '0.5rem 0.75rem' },
      columnTitle: { fontWeight: '600', fontSize: '0.6875rem' },
      row: { background: 'transparent', hoverBackground: t.hover, color: t.text },
      bodyCell: { borderColor: t.hairline, padding: '0.625rem 0.75rem' },
    },
    tabs: {
      tablist: { background: 'transparent', borderColor: t.hairline },
      tab: { color: t.textSecondary, hoverColor: t.text, activeColor: t.primary, padding: '0.625rem 1rem', fontWeight: '600' },
      tabpanel: { background: 'transparent', padding: '0' },
      activeBar: { height: '2px', background: t.primary },
    },
    splitter: {
      root: { background: 'transparent', borderColor: t.hairline },
      gutter: { background: t.hairline },
      handle: { size: '2.5rem', background: t.textSecondary },
    },
    dialog: {
      title: { fontSize: '1.125rem', fontWeight: '600' },
    },
    selectbutton: {
      root: { borderRadius: '4px' },
    },
    togglebutton: {
      root: {
        background: t.card, checkedBackground: t.selected, hoverBackground: t.hover,
        borderColor: t.control, checkedBorderColor: t.primary,
        color: t.textSecondary, hoverColor: t.text, checkedColor: t.selectedText,
        borderRadius: '4px', padding: '0.25rem 0.625rem', fontWeight: '500',
      },
      content: { checkedBackground: 'transparent', checkedShadow: 'none', padding: '0', borderRadius: '2px' },
      icon: { color: t.textSecondary, hoverColor: t.text, checkedColor: t.selectedText },
    },
    progressbar: {
      root: { background: t.sunken, borderRadius: '2px', height: '4px' },
      value: { background: t.primary },
    },
    skeleton: {
      root: { background: t.sunken, animationBackground: ld('rgba(255, 255, 255, 0.5)', 'rgba(255, 255, 255, 0.04)') },
    },
  },
  extend: {
    lx: t,
  },
});
