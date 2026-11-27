import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { PALETTE } from './theme/tokens';

/**
 * The source scan of design system v3 (docs/DESIGN.md "Quality bar and testing contract"). Colour lives in one place,
 * `theme/` (the token table and the preset); every other file reaches it through --p-lx-* or the preset's tokens. And
 * the v2 vocabulary that made the interface look generated (rings, the state map, count-ups, pills, blur, duotone
 * icons) stays retired.
 */
const SRC = resolve(process.cwd(), 'src');
const THEME = join(SRC, 'app', 'theme');
const SELF = join(SRC, 'app', 'source-scan.spec.ts');

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) { return path === THEME ? [] : files(path); }
    return /\.(ts|html|css)$/.test(name) && path !== SELF ? [path] : [];
  });
}

const SOURCES = files(SRC).map((path) => ({ path: relative(SRC, path), text: readFileSync(path, 'utf8') }));

/** A hex colour of 3, 4, 6 or 8 digits that is not part of a longer word (`#main` or `#field` are references, not colours). */
const HEX = /#(?:[0-9a-f]{8}|[0-9a-f]{6}|[0-9a-f]{3,4})(?![\w-])/gi;
const COLOUR_FUNCTION = /\b(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch)\(/gi;

/** The CSS named colours (CSS Color 4), so `color: white` is caught as surely as `color: #fff`. */
const NAMED = new Set(`aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue blueviolet brown burlywood
cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray darkgreen darkgrey
darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen darkslateblue darkslategray darkslategrey
darkturquoise darkviolet deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite forestgreen fuchsia gainsboro ghostwhite
gold goldenrod gray green greenyellow grey honeydew hotpink indianred indigo ivory khaki lavender lavenderblush lawngreen lemonchiffon
lightblue lightcoral lightcyan lightgoldenrodyellow lightgray lightgreen lightgrey lightpink lightsalmon lightseagreen lightskyblue
lightslategray lightslategrey lightsteelblue lightyellow lime limegreen linen magenta maroon mediumaquamarine mediumblue mediumorchid
mediumpurple mediumseagreen mediumslateblue mediumspringgreen mediumturquoise mediumvioletred midnightblue mintcream mistyrose moccasin
navajowhite navy oldlace olive olivedrab orange orangered orchid palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff
peru pink plum powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna silver
skyblue slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato turquoise violet wheat white whitesmoke yellow
yellowgreen`.split(/\s+/));

/** The CSS a file carries: a stylesheet whole, a component's inline `styles`, and a template's `style` attributes. */
function css(path: string, text: string): string {
  if (path.endsWith('.css')) { return text; }
  if (path.endsWith('.html')) { return [...text.matchAll(/\sstyle="([^"]*)"/g)].map((m) => m[1]).join(';'); }
  return [...text.matchAll(/\bstyles:\s*`([^`]*)`/g)].map((m) => m[1]).join('\n');
}

function namedColours(path: string, text: string): string[] {
  const declarations = css(path, text).replace(/\/\*[\s\S]*?\*\//g, '').replace(/(['"])(?:(?!\1).)*\1/g, '');
  return [...declarations.matchAll(/[\w-]+\s*:\s*([^;{}]+)/g)]
    .flatMap((m) => m[1].toLowerCase().split(/[^a-z]+/))
    .filter((word) => NAMED.has(word));
}

/** The retired v2 vocabulary (docs/ROADMAP.md UI3-0, item 2). */
const RETIRED: [string, RegExp][] = [
  ['ProgressRing', /\bProgressRing\b|lx-progress-ring/],
  ['StateMap', /\bStateMap\b|lx-state-map/],
  ['lx-stat', /lx-stat(?![\w-])/],
  ['count-up', /count-up(?![\w-])|\bCountUp\b/],
  ['p-tag', /\bp-tag(?![\w-])|primeng\/tag\b/],
  ['p-chip', /\bp-chip(?![\w-])|primeng\/chip\b/],
  ['border-radius: 999', /border-radius:\s*999/],
  ['9999px', /9999px/],
  ['rounded-full', /rounded-full/],
  ['backdrop-filter', /backdrop-filter/],
  ['duotone', /duotone/i],
];

/** Every --p-lx-* name the preset publishes: the table's colours, the derived shade and the overlay elevation. */
const PUBLISHED = new Set([...Object.keys(PALETTE), 'overlay'].map((name) => `--p-lx-${name.replace(/([a-z])([A-Z])/g, '$1-$2').replace(/([a-z])(\d)/g, '$1-$2').toLowerCase()}`));

describe('source scan', () => {
  it('reads the whole application', () => {
    expect(SOURCES.some((s) => s.path === 'styles.css')).toBe(true);
    expect(SOURCES.some((s) => s.path === 'app/shell/shell.ts')).toBe(true);
    expect(SOURCES.some((s) => s.path.startsWith('app/theme/'))).toBe(false);
  });

  it('finds no colour literal outside theme/', () => {
    const found = SOURCES.flatMap(({ path, text }) => [
      ...[...text.matchAll(HEX)].map((m) => `${path}: ${m[0]}`),
      ...[...text.matchAll(COLOUR_FUNCTION)].map((m) => `${path}: ${m[0]}`),
      ...namedColours(path, text).map((word) => `${path}: ${word}`),
    ]);
    expect(found).toEqual([]);
  });

  it('finds none of the retired v2 names', () => {
    const found = SOURCES.flatMap(({ path, text }) => RETIRED.filter(([, pattern]) => pattern.test(text)).map(([name]) => `${path}: ${name}`));
    expect(found).toEqual([]);
  });

  it('uses only the colour tokens the preset publishes', () => {
    const unknown = SOURCES.flatMap(({ path, text }) => [...text.matchAll(/--p-lx-[a-z0-9-]+/g)].map((m) => m[0]).filter((name) => !PUBLISHED.has(name)).map((name) => `${path}: ${name}`));
    expect(PUBLISHED.has('--p-lx-ink-2')).toBe(true);
    expect(unknown).toEqual([]);
  });

  it('catches what it is meant to catch', () => {
    expect('color: #FFF;'.match(HEX)).toEqual(['#FFF']);
    expect('<div #field (click)="x">'.match(HEX)).toBeNull();
    expect('box-shadow: 0 0 0 1px rgb(0 0 0 / .1)'.match(COLOUR_FUNCTION)).toEqual(['rgb(']);
    expect(namedColours('a.css', '.a { background: white; font-family: "Snow Serif"; color: var(--p-lx-ink); }')).toEqual(['white']);
    expect(RETIRED.find(([name]) => name === 'lx-stat')![1].test('lx-status')).toBe(false);
  });
});
