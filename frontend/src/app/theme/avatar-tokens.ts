import auraAvatar from '@primeuix/themes/aura/avatar';
import { lexvayTokens as t } from './lexvay-preset';

/** Avatars as 32 px discs: a person's initials in accent on accent-tint (audit trail, People). */
export const lexvayAvatar = {
  ...auraAvatar,
  root: { ...auraAvatar.root, width: '2rem', height: '2rem', fontSize: '0.75rem', fontWeight: '600', background: t.accentTint, color: t.accent },
};
