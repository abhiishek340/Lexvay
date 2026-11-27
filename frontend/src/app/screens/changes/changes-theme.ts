import auraProgressspinner from '@primeuix/themes/aura/progressspinner';
import { lexvayDatatable } from '../../theme/datatable-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTag } from '../../theme/tag-tokens';

/** Component token sets the Changes screen renders (registered by the screen with registerComponentTokens). */
export const CHANGES_TOKENS = { datatable: lexvayDatatable, message: lexvayMessage, progressspinner: auraProgressspinner, skeleton: lexvaySkeleton, tag: lexvayTag };
