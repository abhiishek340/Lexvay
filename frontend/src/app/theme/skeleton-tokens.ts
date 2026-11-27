import auraSkeleton from '@primeuix/themes/aura/skeleton';
import { lexvayTokens as t } from './lexvay-preset';

/** Skeletons shaped like the content, on surface-2 with a soft shimmer (1.4 s, styles.css). */
export const lexvaySkeleton = {
  ...auraSkeleton,
  root: { ...auraSkeleton.root, borderRadius: '8px', background: t.surface2, animationBackground: 'light-dark(rgb(255 255 255 / .6), rgb(255 255 255 / .04))' },
};
