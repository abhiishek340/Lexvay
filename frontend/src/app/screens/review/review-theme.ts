import auraRadiobutton from '@primeuix/themes/aura/radiobutton';
import { lexvayDialog } from '../../theme/dialog-tokens';
import { lexvayTokens as t } from '../../theme/lexvay-preset';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTabs } from '../../theme/tabs-tokens';

/**
 * Component token sets the Review screen renders and the shell does not (registered with registerComponentTokens). The
 * DecisionBar and the DocumentViewer register their own. The signing dialog's radios are ink like every checked control,
 * with an ink-2 ring that reaches 3:1.
 */
export const REVIEW_TOKENS = {
  dialog: lexvayDialog,
  message: lexvayMessage,
  radiobutton: {
    ...auraRadiobutton,
    root: {
      ...auraRadiobutton.root, borderColor: t.ink2, hoverBorderColor: t.ink, checkedBorderColor: t.ink, checkedHoverBorderColor: t.inkHover, checkedBackground: t.ink,
      checkedHoverBackground: t.inkHover, focusRing: { ...auraRadiobutton.root.focusRing, color: t.accent, shadow: 'none' },
    },
    icon: { ...auraRadiobutton.icon, checkedColor: t.paper, checkedHoverColor: t.paper },
  },
  skeleton: lexvaySkeleton,
  tabs: lexvayTabs,
};
