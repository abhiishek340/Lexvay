import auraDrawer from '@primeuix/themes/aura/drawer';
import { lexvayTokens as t } from './lexvay-preset';

/** Drawer as a raised sheet that slides in from the right (DESIGN.md "Overlays"): e3, a quiet header, a 20 px title. */
export const lexvayDrawer = {
  ...auraDrawer,
  root: { ...auraDrawer.root, background: t.raised, borderColor: 'transparent', color: t.text, shadow: t.e3 },
  header: { ...auraDrawer.header, padding: '1.25rem 1.5rem 1rem' },
  title: { ...auraDrawer.title, fontSize: '1.25rem', fontWeight: '620' },
  content: { ...auraDrawer.content, padding: '0 1.5rem 1.5rem 1.5rem' },
};
