import auraFileUpload from '@primeuix/themes/aura/fileupload';
import { lexvayTokens as t } from './lexvay-preset';

/** FileUpload without a frame or a toolbar of its own: its content area is the drop zone the screen draws (report check). */
export const lexvayFileUpload = {
  ...auraFileUpload,
  root: { ...auraFileUpload.root, background: 'transparent', borderColor: 'transparent', color: t.text, borderRadius: '12px' },
  header: { ...auraFileUpload.header, padding: '0', gap: '0' },
  content: { ...auraFileUpload.content, highlightBorderColor: t.accent, padding: '0', gap: '0' },
};
