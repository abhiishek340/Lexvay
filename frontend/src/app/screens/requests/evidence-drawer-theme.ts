import { updatePreset } from '@primeuix/themes';
import auraDrawer from '@primeuix/themes/aura/drawer';
import { registerRegisterTheme } from '../register/register-theme';

let registered = false;

/**
 * The token sets of the components only the answer drawer renders: Drawer, and ToggleSwitch with the register's
 * Lexvay colours (registered once by the register's own function, so the two screens share one definition). They join
 * the active preset when the drawer's chunk first runs, before either component is created (D-082, D-083).
 */
export function registerEvidenceDrawerTheme(): void {
  registerRegisterTheme();
  if (registered) { return; }
  registered = true;
  updatePreset({ components: { drawer: auraDrawer } });
}
