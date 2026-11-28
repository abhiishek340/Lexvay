import auraSelect from '@primeuix/themes/aura/select';
import { lexvayDatatable } from '../../theme/datatable-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvayTabs } from '../../theme/tabs-tokens';
import { lexvayToggleswitch } from '../../theme/toggleswitch-tokens';

/** Component token sets the register renders (registered by the screen with registerComponentTokens). */
export const REGISTER_TOKENS = {
  datatable: lexvayDatatable, message: lexvayMessage, select: auraSelect, tabs: lexvayTabs,
  toggleswitch: lexvayToggleswitch,
};
