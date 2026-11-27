import auraSelectbutton from '@primeuix/themes/aura/selectbutton';
import auraTogglebutton from '@primeuix/themes/aura/togglebutton';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * SelectButton as a segmented control: a quiet track with the chosen segment raised onto it. Shared by the chunks
 * that render one (the account menu's theme choice, the review filters), registered with registerComponentTokens.
 */
export const SEGMENTED_TOKENS = {
  selectbutton: { ...auraSelectbutton, root: { ...auraSelectbutton.root, borderRadius: '10px' } },
  togglebutton: {
    ...auraTogglebutton,
    root: {
      ...auraTogglebutton.root,
      padding: '0.1875rem', borderRadius: '10px', gap: '0.375rem', fontWeight: '550',
      background: t.surface2, checkedBackground: t.surface2, hoverBackground: t.surface2,
      borderColor: t.surface2, checkedBorderColor: t.surface2,
      color: t.text2, hoverColor: t.text, checkedColor: t.text,
    },
    content: { ...auraTogglebutton.content, padding: '0.25rem 0.75rem', borderRadius: '7px', checkedBackground: t.raised, checkedShadow: t.e1 },
    icon: { ...auraTogglebutton.icon, color: t.text2, hoverColor: t.text, checkedColor: t.text },
  },
};
