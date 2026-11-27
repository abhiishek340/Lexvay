import auraToggleswitch from '@primeuix/themes/aura/toggleswitch';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * The ToggleSwitch token set, shared by the screens that render one (Register, Requests). Lexvay colours replace
 * Aura's: the track is the form-control colour unchecked and primary checked, the handle the card colour on grey and
 * the on-primary colour on primary, so track against card and handle against track both reach 3:1 (WCAG 1.4.11) in
 * either theme.
 */
export const lexvayToggleswitch = {
  root: { ...auraToggleswitch.root, background: t.control, hoverBackground: t.textSecondary, checkedBackground: t.primary, checkedHoverBackground: t.primaryHover },
  handle: { ...auraToggleswitch.handle, background: t.card, hoverBackground: t.card, checkedBackground: t.onPrimary, checkedHoverBackground: t.onPrimary },
};
