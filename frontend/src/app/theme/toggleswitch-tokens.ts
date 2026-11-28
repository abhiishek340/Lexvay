import auraToggleswitch from '@primeuix/themes/aura/toggleswitch';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * The ToggleSwitch token set, shared by the screens that render one (Register, Requests). The track is ink-3 off and
 * ink on, the handle surface off and paper on, so track against the page and handle against track both reach 3:1
 * (WCAG 1.4.11) in either theme; the switch carries no colour of its own.
 */
export const lexvayToggleswitch = {
  ...auraToggleswitch,
  root: {
    ...auraToggleswitch.root, background: t.ink3, hoverBackground: t.ink2, checkedBackground: t.ink, checkedHoverBackground: t.inkHover,
    focusRing: { ...auraToggleswitch.root.focusRing, color: t.accent, shadow: 'none' },
  },
  handle: { ...auraToggleswitch.handle, background: t.surface, hoverBackground: t.surface, checkedBackground: t.paper, checkedHoverBackground: t.paper },
};
