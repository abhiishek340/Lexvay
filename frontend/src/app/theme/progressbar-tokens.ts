import auraProgressbar from '@primeuix/themes/aura/progressbar';
import { lexvayTokens as t } from './lexvay-preset';

/** The ProgressBar token set with Lexvay's track and fill, used by Review. */
export const lexvayProgressbar = {
  ...auraProgressbar,
  root: { ...auraProgressbar.root, background: t.surface2, borderRadius: '999px', height: '6px' },
  value: { ...auraProgressbar.value, background: t.accent },
};
