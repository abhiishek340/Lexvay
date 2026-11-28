import auraMessage from '@primeuix/themes/aura/message';
import { lexvayTokens as t } from './lexvay-preset';

const severity = (color: string) => ({
  background: 'transparent', borderColor: 'transparent', color, shadow: 'none',
  closeButton: { hoverBackground: t.sunken, focusRing: { color: t.accent, shadow: 'none' } },
  outlined: { color, borderColor: t.lineStrong },
  simple: { color },
});

/**
 * Message as plain text beside what it is about (DESIGN.md "States and feedback"): no fill and no box, the words in
 * `missing` for an error and `found` for a success, everything else in ink-2. There is no warning colour.
 */
export const lexvayMessage = {
  ...auraMessage,
  root: { ...auraMessage.root, borderRadius: '0', borderWidth: '0' },
  content: { ...auraMessage.content, padding: '0', gap: '0.5rem', sm: { padding: '0' }, lg: { padding: '0' } },
  text: { ...auraMessage.text, fontSize: '0.8125rem', fontWeight: '400', sm: { fontSize: '0.75rem' }, lg: { fontSize: '0.875rem' } },
  info: severity(t.ink2),
  success: severity(t.found),
  warn: severity(t.ink2),
  error: severity(t.missing),
  secondary: severity(t.ink2),
  contrast: severity(t.ink),
};
