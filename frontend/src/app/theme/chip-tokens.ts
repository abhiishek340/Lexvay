import auraChip from '@primeuix/themes/aura/chip';
import { lexvayTokens as t } from './lexvay-preset';

/** Chips drawn like Labels (ui/label.ts): sunken, 6 px radius, ink text, the remove icon in ink-2 (search terms, chosen states). */
export const lexvayChip = {
  ...auraChip,
  root: { ...auraChip.root, borderRadius: '6px', paddingX: '0.5rem', paddingY: '0.125rem', gap: '0.375rem', background: t.sunken, focusBackground: t.line, color: t.ink },
  icon: { ...auraChip.icon, color: t.ink2 },
  removeIcon: { ...auraChip.removeIcon, color: t.ink2, focusRing: { width: '2px', style: 'solid', color: t.accent, offset: '1px', shadow: 'none' } },
};
