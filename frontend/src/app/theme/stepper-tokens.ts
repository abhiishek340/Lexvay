import auraStepper from '@primeuix/themes/aura/stepper';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * Stepper as Lexvay draws progress: numbered steps on the surface, the active one in accent with its tint behind the
 * number, a 2 px line between steps that turns accent once passed. Panels sit on the page, not in a box of their own.
 */
export const lexvayStepper = {
  ...auraStepper,
  separator: { ...auraStepper.separator, background: t.line, activeBackground: t.accent, size: '2px', margin: '0' },
  step: { ...auraStepper.step, padding: '0.25rem', gap: '0.5rem' },
  stepHeader: { ...auraStepper.stepHeader, borderRadius: '10px', gap: '0.625rem', focusRing: { width: '2px', style: 'solid', color: t.accent, offset: '2px', shadow: 'none' } },
  stepTitle: { ...auraStepper.stepTitle, color: t.text2, activeColor: t.text, fontWeight: '550', fontSize: '0.875rem' },
  stepNumber: {
    ...auraStepper.stepNumber,
    background: t.surface2, activeBackground: t.accent, borderColor: 'transparent', activeBorderColor: 'transparent',
    color: t.text2, activeColor: t.onAccent, size: '1.75rem', fontSize: '0.8125rem', fontWeight: '600', shadow: 'none',
  },
  steppanels: { ...auraStepper.steppanels, padding: '0' },
  steppanel: { ...auraStepper.steppanel, background: 'transparent', color: t.text, padding: '0', indent: '0' },
};
