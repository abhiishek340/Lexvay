import auraAccordion from '@primeuix/themes/aura/accordion';
import auraConfirmdialog from '@primeuix/themes/aura/confirmdialog';
import auraDatepicker from '@primeuix/themes/aura/datepicker';
import auraDialog from '@primeuix/themes/aura/dialog';
import auraInputnumber from '@primeuix/themes/aura/inputnumber';
import auraListbox from '@primeuix/themes/aura/listbox';
import auraTextarea from '@primeuix/themes/aura/textarea';

/** Component token sets the Rules screen renders and the shell does not (registered with registerComponentTokens, D-246). */
export const RULES_TOKENS = {
  accordion: auraAccordion,
  confirmdialog: auraConfirmdialog,
  datepicker: auraDatepicker,
  dialog: auraDialog,
  inputnumber: auraInputnumber,
  listbox: auraListbox,
  textarea: auraTextarea,
};
