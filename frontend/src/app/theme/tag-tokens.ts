import auraTag from '@primeuix/themes/aura/tag';
import { lexvayTokens as t } from './lexvay-preset';

/** Tags as pills: the status colour on its tint, with the word and the icon carrying the state, never the colour alone. */
export const lexvayTag = {
  ...auraTag,
  root: { ...auraTag.root, fontSize: '0.75rem', fontWeight: '550', padding: '0.125rem 0.625rem', gap: '0.375rem', borderRadius: '999px', roundedBorderRadius: '999px' },
  icon: { ...auraTag.icon, size: '0.875rem' },
  primary: { background: t.accentTint, color: t.accent },
  info: { background: t.accentTint, color: t.accent },
  secondary: { background: t.surface2, color: t.text2 },
  success: { background: t.foundTint, color: t.found },
  warn: { background: t.ambiguousTint, color: t.ambiguous },
  danger: { background: t.missingTint, color: t.missing },
  contrast: { background: t.text, color: t.surface },
};
