import auraDrawer from '@primeuix/themes/aura/drawer';
import auraSelect from '@primeuix/themes/aura/select';
import auraTextarea from '@primeuix/themes/aura/textarea';
import { lexvayDatatable } from '../../theme/datatable-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { SEGMENTED_TOKENS } from '../../theme/segmented-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTag } from '../../theme/tag-tokens';
import { lexvayToggleswitch } from '../../theme/toggleswitch-tokens';

/** Component token sets the inbox and its answer drawer render (registered with registerComponentTokens). */
export const REQUESTS_TOKENS = {
  ...SEGMENTED_TOKENS,
  datatable: lexvayDatatable, drawer: auraDrawer, message: lexvayMessage, select: auraSelect, skeleton: lexvaySkeleton, tag: lexvayTag,
  textarea: auraTextarea, toggleswitch: lexvayToggleswitch,
};
