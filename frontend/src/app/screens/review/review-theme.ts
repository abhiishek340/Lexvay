import auraRadiobutton from '@primeuix/themes/aura/radiobutton';
import auraSplitter from '@primeuix/themes/aura/splitter';
import auraTextarea from '@primeuix/themes/aura/textarea';
import { lexvayDialog } from '../../theme/dialog-tokens';
import { lexvayTokens as t } from '../../theme/lexvay-preset';
import { lexvayMessage } from '../../theme/message-tokens';
import { lexvayProgressbar } from '../../theme/progressbar-tokens';
import { SEGMENTED_TOKENS } from '../../theme/segmented-tokens';
import { lexvaySkeleton } from '../../theme/skeleton-tokens';
import { lexvayTabs } from '../../theme/tabs-tokens';

/** Component token sets the Review screen renders and the shell does not (registered with registerComponentTokens). */
export const REVIEW_TOKENS = {
  ...SEGMENTED_TOKENS,
  dialog: lexvayDialog,
  message: lexvayMessage,
  progressbar: lexvayProgressbar,
  radiobutton: {
    ...auraRadiobutton,
    root: { ...auraRadiobutton.root, borderColor: t.lineStrong, hoverBorderColor: t.text2, checkedBorderColor: t.accent, checkedHoverBorderColor: t.accentHover, checkedBackground: t.accent, checkedHoverBackground: t.accentHover },
    icon: { ...auraRadiobutton.icon, checkedColor: t.onAccent, checkedHoverColor: t.onAccent },
  },
  skeleton: lexvaySkeleton,
  splitter: {
    ...auraSplitter,
    root: { ...auraSplitter.root, background: 'transparent', borderColor: t.line },
    gutter: { ...auraSplitter.gutter, background: t.line },
    handle: { ...auraSplitter.handle, size: '2.5rem', background: t.text2 },
  },
  tabs: lexvayTabs,
  textarea: auraTextarea,
};
