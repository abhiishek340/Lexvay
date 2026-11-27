import auraDrawer from '@primeuix/themes/aura/drawer';
import auraTextarea from '@primeuix/themes/aura/textarea';
import { lexvayToggleswitch } from '../../theme/toggleswitch-tokens';

/** Component token sets the inbox and its answer drawer render and the shell does not (registered with registerComponentTokens). */
export const REQUESTS_TOKENS = { drawer: auraDrawer, textarea: auraTextarea, toggleswitch: lexvayToggleswitch };
