import auraRadiobutton from '@primeuix/themes/aura/radiobutton';
import { lexvayDialog } from '../../theme/dialog-tokens';
import { lexvayTokens as t } from '../../theme/lexvay-preset';
import { lexvayMessage } from '../../theme/message-tokens';
import { SEGMENTED_TOKENS } from '../../theme/segmented-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTabs } from '../../theme/tabs-tokens';

/**
 * Component token sets the Review screen renders and the shell does not (registered with registerComponentTokens). The
 * DecisionBar and the DocumentViewer register their own.
 */
export const REVIEW_TOKENS = {
  ...SEGMENTED_TOKENS,
  dialog: lexvayDialog,
  message: lexvayMessage,
  radiobutton: {
    ...auraRadiobutton,
    root: { ...auraRadiobutton.root, borderColor: t.lineStrong, hoverBorderColor: t.ink2, checkedBorderColor: t.accent, checkedHoverBorderColor: t.inkHover, checkedBackground: t.accent, checkedHoverBackground: t.inkHover },
    icon: { ...auraRadiobutton.icon, checkedColor: t.paper, checkedHoverColor: t.paper },
  },
  skeleton: lexvaySkeleton,
  tabs: lexvayTabs,
};
