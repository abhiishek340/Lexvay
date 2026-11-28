import auraCheckbox from '@primeuix/themes/aura/checkbox';
import auraMultiselect from '@primeuix/themes/aura/multiselect';
import { lexvayChip } from './chip-tokens';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * MultiSelect on the preset's form-field and list tokens: the chosen options as Label-like chips, a focused option on
 * sunken. Its option checkboxes (bordered in ink-2 so they reach 3:1, checked in ink) and its chips come with it, so a
 * chunk that renders one registers this whole group.
 */
export const MULTISELECT_TOKENS = {
  multiselect: {
    ...auraMultiselect,
    chip: { ...auraMultiselect.chip, borderRadius: '6px' },
    option: { ...auraMultiselect.option, focusBackground: t.sunken, gap: '0.5rem' },
  },
  checkbox: {
    ...auraCheckbox,
    root: {
      ...auraCheckbox.root, borderRadius: '4px', borderColor: t.ink2, hoverBorderColor: t.ink, checkedBackground: t.ink, checkedHoverBackground: t.inkHover,
      checkedBorderColor: t.ink, checkedHoverBorderColor: t.inkHover, focusRing: { ...auraCheckbox.root.focusRing, color: t.accent, shadow: 'none' },
    },
    icon: { ...auraCheckbox.icon, checkedColor: t.paper, checkedHoverColor: t.paper },
  },
  chip: lexvayChip,
};
