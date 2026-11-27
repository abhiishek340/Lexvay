import auraMessage from '@primeuix/themes/aura/message';
import { lexvayTokens as t } from './lexvay-preset';

const severity = (fg: string, bg: string) => ({
  background: bg, borderColor: 'transparent', color: fg, shadow: 'none',
  closeButton: { hoverBackground: t.surface2, focusRing: { color: t.accent, shadow: 'none' } },
  outlined: { color: fg, borderColor: fg },
  simple: { color: fg },
});

/** Message as a tinted, borderless note in the status colour (every chunk that shows one registers it). */
export const lexvayMessage = {
  ...auraMessage,
  root: { ...auraMessage.root, borderRadius: '8px', borderWidth: '0' },
  content: { ...auraMessage.content, padding: '0.625rem 0.875rem', gap: '0.625rem' },
  info: severity(t.accent, t.accentTint),
  success: severity(t.found, t.foundTint),
  warn: severity(t.ambiguous, t.ambiguousTint),
  error: severity(t.missing, t.missingTint),
  secondary: severity(t.text2, t.surface2),
};
