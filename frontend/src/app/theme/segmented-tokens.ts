import auraSelectbutton from '@primeuix/themes/aura/selectbutton';
import auraTogglebutton from '@primeuix/themes/aura/togglebutton';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * SelectButton as a segmented control: one bordered field whose chosen segment is sunken with ink text. Shared by the
 * chunks that render one (the account menu's theme choice, the requests inbox filter), registered with registerComponentTokens.
 */
export const SEGMENTED_TOKENS = {
  selectbutton: { ...auraSelectbutton, root: { ...auraSelectbutton.root, borderRadius: '6px' } },
  togglebutton: {
    ...auraTogglebutton,
    root: {
      ...auraTogglebutton.root,
      padding: '0.1875rem', borderRadius: '6px', gap: '0.375rem', fontWeight: '500', fontSize: '0.8125rem',
      background: t.surface, checkedBackground: t.surface, hoverBackground: t.surface,
      borderColor: t.lineStrong, checkedBorderColor: t.lineStrong,
      color: t.ink2, hoverColor: t.ink, checkedColor: t.ink,
      focusRing: { ...auraTogglebutton.root.focusRing, color: t.accent, shadow: 'none' },
      sm: { fontSize: '0.8125rem', padding: '0.1875rem' },
    },
    content: { ...auraTogglebutton.content, padding: '0.125rem 0.625rem', borderRadius: '4px', checkedBackground: t.sunken, checkedShadow: 'none' },
    icon: { ...auraTogglebutton.icon, color: t.ink2, hoverColor: t.ink, checkedColor: t.ink },
  },
};
