import { updatePreset } from '@primeuix/themes';
import auraToggleswitch from '@primeuix/themes/aura/toggleswitch';
import { lexvayTokens as t } from '../../theme/lexvay-preset';

let registered = false;

/**
 * The ToggleSwitch token set, which only the register renders. It joins the active preset when the register chunk
 * first runs, like the Rules screen's components (D-082, D-083). Lexvay colours replace Aura's: the track is the
 * form-control boundary colour unchecked and accent checked, the handle the surface colour on grey and the on-accent colour
 * on accent, so track against surface and handle against track both reach 3:1 (WCAG 1.4.11) in either theme.
 */
export function registerRegisterTheme(): void {
  if (registered) { return; }
  registered = true;
  updatePreset({
    components: {
      toggleswitch: {
        root: { ...auraToggleswitch.root, background: t.lineStrong, hoverBackground: t.text2, checkedBackground: t.accent, checkedHoverBackground: t.accentHover },
        handle: { ...auraToggleswitch.handle, background: t.surface, hoverBackground: t.surface, checkedBackground: t.onAccent, checkedHoverBackground: t.onAccent },
      },
    },
  });
}
