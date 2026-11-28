import auraDatatable from '@primeuix/themes/aura/datatable';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * DataTable as DESIGN.md "Tables" draws it: 40 px rows (9 px above and below a 20 px line and the 1 px rule), a `line`
 * rule between rows, no outer card, the header in meta type on paper over a `line-strong` rule, hover in sunken and the
 * selected row in accent-wash (its 2 px accent rule is drawn in styles.css).
 */
export const lexvayDatatable = {
  ...auraDatatable,
  header: { ...auraDatatable.header, background: 'transparent', borderColor: t.line, color: t.ink, padding: '0' },
  headerCell: {
    ...auraDatatable.headerCell, background: t.paper, hoverBackground: t.paper, selectedBackground: t.paper, borderColor: t.lineStrong,
    color: t.ink2, hoverColor: t.ink, selectedColor: t.ink, padding: '0.75rem 0.75rem',
    focusRing: { ...auraDatatable.headerCell.focusRing, color: t.accent, shadow: 'none' },
  },
  columnTitle: { ...auraDatatable.columnTitle, fontWeight: '500' },
  row: {
    ...auraDatatable.row, background: 'transparent', hoverBackground: t.sunken, selectedBackground: t.accentWash, color: t.ink, hoverColor: t.ink,
    selectedColor: t.ink, stripedBackground: 'transparent', focusRing: { ...auraDatatable.row.focusRing, color: t.accent, shadow: 'none' },
  },
  bodyCell: { ...auraDatatable.bodyCell, borderColor: t.line, padding: '0.5625rem 0.75rem', selectedBorderColor: t.line },
  footerCell: { ...auraDatatable.footerCell, background: 'transparent', borderColor: t.line, color: t.ink },
  sortIcon: { ...auraDatatable.sortIcon, color: t.ink2, hoverColor: t.ink, size: '0.75rem' },
};
