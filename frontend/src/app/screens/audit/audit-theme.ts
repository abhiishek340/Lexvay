import auraSelect from '@primeuix/themes/aura/select';
import { lexvayAvatar } from '../../theme/avatar-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTimeline } from '../../theme/timeline-tokens';

/** Component token sets the Audit trail renders (registered by the screen with registerComponentTokens). */
export const AUDIT_TOKENS = { avatar: lexvayAvatar, message: lexvayMessage, select: auraSelect, skeleton: lexvaySkeleton, timeline: lexvayTimeline };
