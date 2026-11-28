import auraStepper from '@primeuix/themes/aura/stepper';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * Stepper as a quiet numbered row: the numbers in tabular ink-2 with no disc, the active step in ink, a hairline between
 * steps. Panels sit on the page, not in a box of their own.
 */
export const lexvayStepper = {
  ...auraStepper,
  separator: { ...auraStepper.separator, background: t.line, activeBackground: t.ink, size: '1px', margin: '0' },
  step: { ...auraStepper.step, padding: '0.25rem', gap: '0.5rem' },
  stepHeader: { ...auraStepper.stepHeader, borderRadius: '6px', gap: '0.5rem', focusRing: { width: '2px', style: 'solid', color: t.accent, offset: '2px', shadow: 'none' } },
  stepTitle: { ...auraStepper.stepTitle, color: t.ink2, activeColor: t.ink, fontWeight: '500', fontSize: '0.8125rem' },
  stepNumber: {
    ...auraStepper.stepNumber,
    background: 'transparent', activeBackground: 'transparent', borderColor: 'transparent', activeBorderColor: 'transparent',
    color: t.ink2, activeColor: t.ink, size: '1.25rem', fontSize: '0.8125rem', fontWeight: '500', shadow: 'none',
  },
  steppanels: { ...auraStepper.steppanels, padding: '0' },
  steppanel: { ...auraStepper.steppanel, background: 'transparent', color: t.ink, padding: '0', indent: '0' },
};
