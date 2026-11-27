import auraChip from '@primeuix/themes/aura/chip';
import { lexvayTokens as t } from './lexvay-preset';

/** Chips as quiet pills on the inset colour (search terms, chosen states), with the remove icon in secondary text. */
export const lexvayChip = {
  ...auraChip,
  root: { ...auraChip.root, borderRadius: '999px', paddingX: '0.625rem', paddingY: '0.1875rem', gap: '0.375rem', background: t.surface2, focusBackground: t.line, color: t.text },
  icon: { ...auraChip.icon, color: t.text2 },
  removeIcon: { ...auraChip.removeIcon, color: t.text2, focusRing: { width: '2px', style: 'solid', color: t.accent, offset: '1px', shadow: 'none' } },
};
