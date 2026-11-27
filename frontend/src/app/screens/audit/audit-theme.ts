import auraSelect from '@primeuix/themes/aura/select';
import { lexvayDatatable } from '../../theme/datatable-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';

/** Component token sets the Audit trail renders (registered by the screen with registerComponentTokens). */
export const AUDIT_TOKENS = { datatable: lexvayDatatable, message: lexvayMessage, select: auraSelect, skeleton: lexvaySkeleton };
