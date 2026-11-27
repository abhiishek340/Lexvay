import auraTabs from '@primeuix/themes/aura/tabs';
import { lexvayTokens as t } from './lexvay-preset';

/** Tabs on the page ground: quiet labels, the active one in text colour over a 2 px accent bar. */
export const lexvayTabs = {
  ...auraTabs,
  tablist: { ...auraTabs.tablist, background: 'transparent', borderColor: t.line },
  tab: { ...auraTabs.tab, color: t.text2, hoverColor: t.text, activeColor: t.text, padding: '0.75rem 1rem', fontWeight: '550', gap: '0.5rem' },
  tabpanel: { ...auraTabs.tabpanel, background: 'transparent', padding: '0' },
  activeBar: { ...auraTabs.activeBar, height: '2px', background: t.accent },
  navButton: { ...auraTabs.navButton, background: t.ground },
};
