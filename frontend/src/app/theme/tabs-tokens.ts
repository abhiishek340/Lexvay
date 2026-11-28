import auraTabs from '@primeuix/themes/aura/tabs';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * Tabs as an underlined text row on paper: ink-2 labels, the active one in ink over a 2 px ink bar, a hairline below.
 * PrimeNG slides its bar by animating width and left, which DESIGN.md "Motion" rules out (transform and opacity only):
 * the bar moves to the chosen tab at once.
 */
export const lexvayTabs = {
  ...auraTabs,
  css: '.p-tablist-active-bar { transition: none; }',
  tablist: { ...auraTabs.tablist, background: 'transparent', borderColor: t.line, borderWidth: '0 0 1px 0' },
  tab: {
    ...auraTabs.tab, background: 'transparent', hoverBackground: 'transparent', activeBackground: 'transparent',
    borderColor: 'transparent', hoverBorderColor: 'transparent', activeBorderColor: 'transparent', borderWidth: '0',
    color: t.ink2, hoverColor: t.ink, activeColor: t.ink, padding: '0.625rem 0', fontWeight: '500', gap: '0.375rem',
    focusRing: { ...auraTabs.tab.focusRing, color: t.accent, offset: '-2px', shadow: 'none' },
  },
  tabpanel: { ...auraTabs.tabpanel, background: 'transparent', padding: '0', color: t.ink },
  activeBar: { ...auraTabs.activeBar, height: '2px', bottom: '-1px', background: t.ink },
  navButton: { ...auraTabs.navButton, background: t.paper, color: t.ink2, hoverColor: t.ink, shadow: 'none' },
};
