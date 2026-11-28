import auraDialog from '@primeuix/themes/aura/dialog';

/** Dialog on the preset's modal overlay tokens (surface, the overlay elevation, 8 px radius) with a Section-type title. */
export const lexvayDialog = {
  ...auraDialog,
  header: { ...auraDialog.header, padding: '1.5rem 1.5rem 1rem', gap: '0.5rem' },
  title: { ...auraDialog.title, fontSize: '0.9375rem', fontWeight: '600' },
  content: { ...auraDialog.content, padding: '0 1.5rem 1.5rem' },
  footer: { ...auraDialog.footer, padding: '0 1.5rem 1.5rem', gap: '0.5rem' },
};
