// Rewrites the SVG strings of SHELL_ICONS in src/app/theme/icons.ts from @ng-icons/phosphor-icons (regular weight).
// The shell keeps its own copy so the icon package module is imported only by lazy chunks (docs/PERF.md, cut 1).
// To add an icon to the shell: add `phosphorName: '',` to SHELL_ICONS, then run `node scripts/shell-icons.mjs`.
// theme/icons.spec.ts checks that every string equals the package's.
import { readFileSync, writeFileSync } from 'node:fs';
import * as regular from '@ng-icons/phosphor-icons/regular';

const FILE = new URL('../src/app/theme/icons.ts', import.meta.url);
const source = readFileSync(FILE, 'utf8');
const block = /(export const SHELL_ICONS = \{\n)([\s\S]*?)(\n\};)/;
const [, open, body, close] = source.match(block);
const names = [...body.matchAll(/^\s+(phosphor[A-Za-z]+):/gm)].map(([, name]) => name).sort();
const lines = names.map((name) => {
  const svg = regular[name];
  if (typeof svg !== 'string') { throw new Error(`${name} is not a regular-weight Phosphor icon`); }
  return `  ${name}: '${svg.replaceAll("'", "\\'")}',`;
});
writeFileSync(FILE, source.replace(block, `${open}${lines.join('\n')}${close}`));
console.log(`SHELL_ICONS: ${names.length} icons written`);
