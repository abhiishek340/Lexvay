import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { DOCUMENT_VIEWER_ICONS } from '../shared/document-viewer/document-viewer';
import { AUDIT_ICONS } from '../screens/audit/audit';
import { CHANGES_ICONS } from '../screens/changes/changes';
import { COVERAGE_ICONS } from '../screens/coverage/coverage';
import { PEOPLE_ICONS } from '../screens/people/people';
import { REGISTER_ICONS } from '../screens/register/register';
import { REQUESTS_ICONS } from '../screens/requests/requests';
import { REVIEW_ICONS } from '../screens/review/review';
import { RULES_ICONS } from '../screens/rules/rules';
import { SETUP_ICONS } from '../screens/setup/setup';
import { WORK_QUEUE_ICONS } from '../shell/work-queue-menu';
import { PALETTE_ICONS } from '../ui/command-palette/command-palette';
import { DECISION_BAR_ICONS } from '../ui/decision-bar/decision-bar';
import * as regular from '@ng-icons/phosphor-icons/regular';
import { SHELL_ICONS } from './icons';

/**
 * An icon that no provider registers renders as nothing (and a console warning), so every name an `ng-icon` can show
 * must be registered by the chunk that shows it: SHELL_ICONS for the shell, sign-in and home screen, a
 * `<SCREEN>_ICONS` constant for a routed screen (provided by its `<screen>.routes.ts`) and the `viewProviders` of a
 * shared component. The names are read from the source: every quoted `phosphor...` in the files a screen reaches
 * through its relative imports and `templateUrl`, stopping at a shared component that registers its own. That covers
 * every branch of every template, which a rendered DOM covers only for the state a test happens to reach. The
 * end-to-end suite adds the runtime half: it fails on any "No icon named" warning.
 */
const SRC = resolve(process.cwd(), 'src/app');
const QUOTED_ICON = /['"`](phosphor[A-Za-z]+)['"`]/g;

interface Unit { name: string; entries: string[]; registered: Record<string, string>; shared?: true }

const UNITS: Unit[] = [
  { name: 'shell, sign-in and home', entries: ['shell/shell.ts', 'screens/login/login.ts', 'screens/home.ts'], registered: SHELL_ICONS },
  // Screens and shared parts whose only icons are the shell's: what they reach must name no other.
  { name: 'overview, report, account menu and toast', entries: ['screens/overview/overview.ts', 'screens/report/report.ts', 'shell/account-menu.ts', 'shell/shell-toast.ts'], registered: {} },
  { name: 'review', entries: ['screens/review/review.ts'], registered: REVIEW_ICONS },
  { name: 'coverage', entries: ['screens/coverage/coverage.ts'], registered: COVERAGE_ICONS },
  { name: 'requests', entries: ['screens/requests/requests.ts'], registered: REQUESTS_ICONS },
  { name: 'changes', entries: ['screens/changes/changes.ts'], registered: CHANGES_ICONS },
  { name: 'rules', entries: ['screens/rules/rules.ts'], registered: RULES_ICONS },
  { name: 'register', entries: ['screens/register/register.ts'], registered: REGISTER_ICONS },
  { name: 'audit', entries: ['screens/audit/audit.ts'], registered: AUDIT_ICONS },
  { name: 'people', entries: ['screens/people/people.ts'], registered: PEOPLE_ICONS },
  { name: 'setup', entries: ['screens/setup/setup.ts'], registered: SETUP_ICONS },
  { name: 'document viewer (shared)', entries: ['shared/document-viewer/document-viewer.ts'], registered: DOCUMENT_VIEWER_ICONS, shared: true },
  { name: 'work queue (shell, deferred)', entries: ['shell/work-queue-menu.ts'], registered: WORK_QUEUE_ICONS, shared: true },
  { name: 'command palette (shell, deferred)', entries: ['ui/command-palette/command-palette.ts'], registered: PALETTE_ICONS, shared: true },
  { name: 'decision bar (shared)', entries: ['ui/decision-bar/decision-bar.ts'], registered: DECISION_BAR_ICONS, shared: true },
];

const read = (file: string): string => readFileSync(join(SRC, file), 'utf8');

/** The files a source file pulls in: relative imports (a .ts file) and its template (`templateUrl`). */
function dependencies(file: string): string[] {
  const text = read(file);
  const found: string[] = [];
  for (const [, spec] of text.matchAll(/from '(\.{1,2}\/[^']+)'/g)) {
    const base = relative(SRC, resolve(SRC, dirname(file), spec));
    const target = [`${base}.ts`, join(base, 'index.ts')].find((candidate) => existsSync(join(SRC, candidate)));
    if (target) { found.push(target); }
  }
  for (const [, url] of text.matchAll(/templateUrl: '(\.\/[^']+)'/g)) { found.push(relative(SRC, resolve(SRC, dirname(file), url))); }
  return found;
}

/** The files of a unit: its entries and what they reach, not entering a shared component that registers its own icons. */
function reach(unit: Unit): Set<string> {
  const seen = new Set<string>();
  const visit = (file: string, entry: boolean): void => {
    if (seen.has(file) || file.endsWith('.spec.ts') || (!entry && read(file).includes('viewProviders'))) { return; }
    seen.add(file);
    dependencies(file).forEach((dependency) => visit(dependency, false));
  };
  unit.entries.forEach((entry) => visit(entry, true));
  return seen;
}

function iconsIn(files: Iterable<string>): Set<string> {
  const names = new Set<string>();
  for (const file of files) {
    for (const [, name] of read(file).matchAll(QUOTED_ICON)) { names.add(name); }
  }
  return names;
}

function sourceFiles(dir = ''): string[] {
  return readdirSync(join(SRC, dir), { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) { return sourceFiles(path); }
    return /\.(ts|html)$/.test(entry.name) && !entry.name.endsWith('.spec.ts') ? [path] : [];
  });
}

describe('icon registration', () => {
  for (const unit of UNITS) {
    it(`${unit.name} registers every icon it shows, and only those`, () => {
      const used = iconsIn(reach(unit));
      const provided = unit === UNITS[0] ? new Set<string>() : new Set(Object.keys(SHELL_ICONS));
      const needed = [...used].filter((name) => !provided.has(name)).sort();
      expect(Object.keys(unit.registered).sort()).toEqual(needed);
      for (const svg of Object.values(unit.registered)) { expect(svg).toContain('<svg'); }
    });
  }

  it("keeps the shell's generated copy of its icons equal to the package", () => {
    for (const [name, svg] of Object.entries(SHELL_ICONS)) {
      expect(svg, name).toBe((regular as Record<string, string>)[name]);
    }
  });

  it('every source file that names an icon belongs to one of the units above', () => {
    const covered = new Set(UNITS.flatMap((unit) => [...reach(unit)]));
    const stray = sourceFiles().filter((file) => iconsIn([file]).size > 0 && !covered.has(file));
    expect(stray).toEqual([]);
  });
});
