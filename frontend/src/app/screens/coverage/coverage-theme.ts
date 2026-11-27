import { lexvayMessage } from '../../theme/message-tokens';
import { SEGMENTED_TOKENS } from '../../theme/segmented-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTabs } from '../../theme/tabs-tokens';

/** Component token sets the Coverage screen renders and the shell does not (registered with registerComponentTokens). */
export const COVERAGE_TOKENS = { ...SEGMENTED_TOKENS, message: lexvayMessage, skeleton: lexvaySkeleton, tabs: lexvayTabs };
