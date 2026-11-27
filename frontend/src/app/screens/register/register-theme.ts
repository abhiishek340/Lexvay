import { updatePreset } from '@primeuix/themes';
import auraToggleswitch from '@primeuix/themes/aura/toggleswitch';
import { lexvayTokens as t } from '../../theme/lexvay-preset';

let registered = false;

/**
 * The ToggleSwitch token set, which only the register renders. It joins the active preset when the register chunk
 * first runs, like the Rules screen's components (D-082, D-083). Lexvay colours replace Aura's: the track is the
 * form-control colour unchecked and primary checked, the handle the card colour on grey and the on-primary colour on
 * primary, so track against card and handle against track both reach 3:1 (WCAG 1.4.11) in either theme.
 */
export function registerRegisterTheme(): void {
  if (registered) { return; }
  registered = true;
  updatePreset({
    components: {
      toggleswitch: {
        root: { ...auraToggleswitch.root, background: t.control, hoverBackground: t.textSecondary, checkedBackground: t.primary, checkedHoverBackground: t.primaryHover },
        handle: { ...auraToggleswitch.handle, background: t.card, hoverBackground: t.card, checkedBackground: t.onPrimary, checkedHoverBackground: t.onPrimary },
      },
    },
  });
}
