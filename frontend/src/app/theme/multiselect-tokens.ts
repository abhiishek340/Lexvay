import auraCheckbox from '@primeuix/themes/aura/checkbox';
import auraMultiselect from '@primeuix/themes/aura/multiselect';
import { lexvayChip } from './chip-tokens';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * MultiSelect on the preset's form-field and list tokens with Lexvay's overlay: the chosen options shown as pills and
 * a focused option on the inset colour. Its option checkboxes (Aura's set, already on the form-field tokens) and its
 * chips come with it, so a chunk that renders one registers this whole group.
 */
export const MULTISELECT_TOKENS = {
  multiselect: {
    ...auraMultiselect,
    chip: { ...auraMultiselect.chip, borderRadius: '999px' },
    option: { ...auraMultiselect.option, focusBackground: t.surface2, gap: '0.625rem' },
  },
  checkbox: auraCheckbox,
  chip: lexvayChip,
};
