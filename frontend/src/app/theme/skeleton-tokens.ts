import auraSkeleton from '@primeuix/themes/aura/skeleton';
import { lexvayTokens as t } from './lexvay-preset';

/** Skeletons the height of the rows they stand for, in sunken, without a shimmer (DESIGN.md "States and feedback"). */
export const lexvaySkeleton = {
  ...auraSkeleton,
  root: { ...auraSkeleton.root, borderRadius: '4px', background: t.sunken, animationBackground: 'transparent' },
};
