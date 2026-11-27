import auraConfirmdialog from '@primeuix/themes/aura/confirmdialog';
import auraIconfield from '@primeuix/themes/aura/iconfield';
import auraInputtext from '@primeuix/themes/aura/inputtext';
import auraMenu from '@primeuix/themes/aura/menu';
import auraSelect from '@primeuix/themes/aura/select';
import { lexvayAvatar } from '../../theme/avatar-tokens';
import { lexvayDatatable } from '../../theme/datatable-tokens';
import { lexvayDialog } from '../../theme/dialog-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTag } from '../../theme/tag-tokens';

/** Component token sets People renders, its dialogs included (registered by the screen with registerComponentTokens). */
export const PEOPLE_TOKENS = {
  avatar: lexvayAvatar, confirmdialog: auraConfirmdialog, datatable: lexvayDatatable, dialog: lexvayDialog, iconfield: auraIconfield,
  inputtext: auraInputtext, menu: auraMenu, message: lexvayMessage, select: auraSelect, skeleton: lexvaySkeleton, tag: lexvayTag,
};
