import auraAvatar from '@primeuix/themes/aura/avatar';
import { lexvayTokens as t } from './lexvay-preset';

/** Avatars as 24 px discs: a person's initials in ink on sunken, as the account button draws them (audit trail, People). */
export const lexvayAvatar = {
  ...auraAvatar,
  root: { ...auraAvatar.root, width: '1.5rem', height: '1.5rem', fontSize: '0.6875rem', background: t.sunken, color: t.ink },
};
