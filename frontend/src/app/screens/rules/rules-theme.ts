import auraAccordion from '@primeuix/themes/aura/accordion';
import auraConfirmdialog from '@primeuix/themes/aura/confirmdialog';
import auraDatepicker from '@primeuix/themes/aura/datepicker';
import auraInputnumber from '@primeuix/themes/aura/inputnumber';
import auraInputtext from '@primeuix/themes/aura/inputtext';
import auraListbox from '@primeuix/themes/aura/listbox';
import auraSelect from '@primeuix/themes/aura/select';
import auraTextarea from '@primeuix/themes/aura/textarea';
import { lexvayDatatable } from '../../theme/datatable-tokens';
import { lexvayDialog } from '../../theme/dialog-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTabs } from '../../theme/tabs-tokens';

/** Component token sets the Rules screen renders and the shell does not (registered with registerComponentTokens, D-246). */
export const RULES_TOKENS = {
  accordion: auraAccordion,
  confirmdialog: auraConfirmdialog,
  datatable: lexvayDatatable,
  datepicker: auraDatepicker,
  dialog: lexvayDialog,
  inputnumber: auraInputnumber,
  inputtext: auraInputtext,
  listbox: auraListbox,
  message: lexvayMessage,
  select: auraSelect,
  skeleton: lexvaySkeleton,
  tabs: lexvayTabs,
  textarea: auraTextarea,
};
