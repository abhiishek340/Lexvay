import auraDialog from '@primeuix/themes/aura/dialog';
import auraRadiobutton from '@primeuix/themes/aura/radiobutton';
import auraSplitter from '@primeuix/themes/aura/splitter';
import auraTextarea from '@primeuix/themes/aura/textarea';
import { lexvayTokens as t } from '../../theme/lexvay-preset';
import { lexvayProgressbar } from '../../theme/progressbar-tokens';

/** Component token sets the Review screen renders and the shell does not (registered with registerComponentTokens). */
export const REVIEW_TOKENS = {
  dialog: auraDialog,
  progressbar: lexvayProgressbar,
  radiobutton: auraRadiobutton,
  splitter: {
    ...auraSplitter,
    root: { ...auraSplitter.root, background: 'transparent', borderColor: t.hairline },
    gutter: { ...auraSplitter.gutter, background: t.hairline },
    handle: { ...auraSplitter.handle, size: '2.5rem', background: t.textSecondary },
  },
  textarea: auraTextarea,
};
