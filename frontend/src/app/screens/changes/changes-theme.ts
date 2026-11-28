import auraProgressspinner from '@primeuix/themes/aura/progressspinner';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTimeline } from '../../theme/timeline-tokens';

/** Component token sets the Changes screen renders (registered by the screen with registerComponentTokens). */
export const CHANGES_TOKENS = { message: lexvayMessage, progressspinner: auraProgressspinner, skeleton: lexvaySkeleton, timeline: lexvayTimeline };
