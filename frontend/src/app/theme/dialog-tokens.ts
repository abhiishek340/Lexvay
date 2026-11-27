import auraDialog from '@primeuix/themes/aura/dialog';

/** Dialog on the preset's modal overlay tokens (raised, e3, 16 px radius), with Lexvay's title. */
export const lexvayDialog = {
  ...auraDialog,
  header: { ...auraDialog.header, padding: '1.5rem 1.5rem 0.75rem', gap: '0.5rem' },
  title: { ...auraDialog.title, fontSize: '1.125rem', fontWeight: '620' },
};
