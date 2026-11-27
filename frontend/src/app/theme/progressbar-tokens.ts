import auraProgressbar from '@primeuix/themes/aura/progressbar';
import { lexvayTokens as t } from './lexvay-preset';

/** The ProgressBar token set with Lexvay's track and fill, shared by the screens that show progress (Overview, Review). */
export const lexvayProgressbar = {
  ...auraProgressbar,
  root: { ...auraProgressbar.root, background: t.sunken, borderRadius: '2px', height: '4px' },
  value: { ...auraProgressbar.value, background: t.primary },
};
