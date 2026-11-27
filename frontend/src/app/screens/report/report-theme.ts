import auraSelect from '@primeuix/themes/aura/select';
import { lexvayDatatable } from '../../theme/datatable-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';

/** Component token sets the Evidence report renders (registered by the screen with registerComponentTokens). */
export const REPORT_TOKENS = { datatable: lexvayDatatable, message: lexvayMessage, select: auraSelect, skeleton: lexvaySkeleton };
