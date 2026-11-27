import auraFileupload from '@primeuix/themes/aura/fileupload';
import { lexvayTokens as t } from './lexvay-preset';

/** FileUpload as a drop zone: a line-strong frame on surface, an accent edge while a file is dragged over it. */
export const lexvayFileupload = {
  ...auraFileupload,
  root: { ...auraFileupload.root, background: t.surface, borderColor: t.lineStrong, color: t.ink, borderRadius: '6px' },
  header: { ...auraFileupload.header, padding: '0.75rem 1rem', gap: '0.5rem' },
  content: { ...auraFileupload.content, highlightBorderColor: t.accent, padding: '0 1rem 1rem 1rem', gap: '0.75rem' },
  file: { ...auraFileupload.file, padding: '0.5rem 0.75rem', gap: '0.75rem', borderColor: t.line },
};
