import { updatePreset } from '@primeuix/themes';
import auraAccordion from '@primeuix/themes/aura/accordion';
import auraConfirmdialog from '@primeuix/themes/aura/confirmdialog';
import auraDatepicker from '@primeuix/themes/aura/datepicker';
import auraListbox from '@primeuix/themes/aura/listbox';

let registered = false;

/**
 * The Aura token sets of the PrimeNG components only the Rules screen renders (Accordion, ConfirmDialog, DatePicker,
 * Listbox). They join the active preset when the Rules chunk first runs, so the initial bundle carries only the
 * tokens of components the shell and every screen need (D-082, D-083). Called before any of them is created.
 */
export function registerRulesTheme(): void {
  if (registered) { return; }
  registered = true;
  updatePreset({ components: { accordion: auraAccordion, confirmdialog: auraConfirmdialog, datepicker: auraDatepicker, listbox: auraListbox } });
}
