import auraInputnumber from '@primeuix/themes/aura/inputnumber';
import auraInputtext from '@primeuix/themes/aura/inputtext';
import auraSelect from '@primeuix/themes/aura/select';
import { lexvayDatatable } from '../../theme/datatable-tokens';
import { lexvayDialog } from '../../theme/dialog-tokens';
import { lexvayDrawer } from '../../theme/drawer-tokens';
import { lexvayFileupload } from '../../theme/fileupload-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { MULTISELECT_TOKENS } from '../../theme/multiselect-tokens';
import { lexvayProgressbar } from '../../theme/progressbar-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayStepper } from '../../theme/stepper-tokens';
import { lexvayTag } from '../../theme/tag-tokens';
import { lexvayToggleswitch } from '../../theme/toggleswitch-tokens';

/**
 * Component token sets the setup workspace renders and the shell does not (registered with registerComponentTokens).
 * Stepper, FileUpload, MultiSelect (with its checkboxes and chips), ToggleSwitch and Drawer carry Lexvay's own sets.
 */
export const SETUP_TOKENS = {
  ...MULTISELECT_TOKENS,
  datatable: lexvayDatatable, dialog: lexvayDialog, drawer: lexvayDrawer, fileupload: lexvayFileupload, inputnumber: auraInputnumber, inputtext: auraInputtext,
  message: lexvayMessage, progressbar: lexvayProgressbar, select: auraSelect, skeleton: lexvaySkeleton, stepper: lexvayStepper, tag: lexvayTag,
  toggleswitch: lexvayToggleswitch,
};
