import auraAvatar from '@primeuix/themes/aura/avatar';
import auraSelect from '@primeuix/themes/aura/select';
import { lexvayTokens as t } from '../../theme/lexvay-preset';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTimeline } from '../../theme/timeline-tokens';

/** Avatars as 32 px discs: a person's initials in accent on accent-tint. */
const lexvayAvatar = {
  ...auraAvatar,
  root: { ...auraAvatar.root, width: '2rem', height: '2rem', fontSize: '0.75rem', fontWeight: '600', background: t.accentTint, color: t.accent },
};

/** Component token sets the Audit trail renders (registered by the screen with registerComponentTokens). */
export const AUDIT_TOKENS = { avatar: lexvayAvatar, message: lexvayMessage, select: auraSelect, skeleton: lexvaySkeleton, timeline: lexvayTimeline };
