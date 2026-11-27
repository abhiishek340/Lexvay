import auraDatatable from '@primeuix/themes/aura/datatable';
import { lexvayTokens as t } from './lexvay-preset';

/** DataTable with 52 px rows (16 px above and below a 20 px line), hairlines inside, a hover tint and no zebra. */
export const lexvayDatatable = {
  ...auraDatatable,
  headerCell: { ...auraDatatable.headerCell, background: 'transparent', color: t.text2, borderColor: t.line, padding: '0.75rem 0.75rem' },
  columnTitle: { ...auraDatatable.columnTitle, fontWeight: '600' },
  row: { ...auraDatatable.row, background: 'transparent', hoverBackground: t.surface2, color: t.text, selectedBackground: t.accentTint, selectedColor: t.text },
  bodyCell: { ...auraDatatable.bodyCell, borderColor: t.line, padding: '1rem 0.75rem' },
};
