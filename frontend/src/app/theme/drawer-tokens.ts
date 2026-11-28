import auraDrawer from '@primeuix/themes/aura/drawer';
import { lexvayTokens as t } from './lexvay-preset';

/** Drawer as an overlay sheet from the right: surface, the overlay elevation, a Section-type title. */
export const lexvayDrawer = {
  ...auraDrawer,
  root: { ...auraDrawer.root, background: t.surface, borderColor: 'transparent', color: t.ink, shadow: t.overlay },
  header: { ...auraDrawer.header, padding: '1.5rem 1.5rem 1rem' },
  title: { ...auraDrawer.title, fontSize: '0.9375rem', fontWeight: '600' },
  content: { ...auraDrawer.content, padding: '0 1.5rem 1.5rem 1.5rem' },
};
