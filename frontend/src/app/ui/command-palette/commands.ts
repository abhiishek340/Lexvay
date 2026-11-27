import { Params } from '@angular/router';
import { Role } from '../../core/models';
import { navLinks } from '../../core/navigation';
import type { IconName } from '../../theme/icons';

export type PaletteAction = 'run-assessment' | 'new-rule-version' | 'download-register';
export type CommandRun =
  | { readonly kind: 'navigate'; readonly commands: readonly unknown[]; readonly queryParams?: Params }
  | { readonly kind: 'action'; readonly action: PaletteAction };
export type CommandGroup = 'Recent' | 'Go to' | 'States' | 'Requirements' | 'Actions';

export interface Command {
  readonly id: string;
  readonly group: Exclude<CommandGroup, 'Recent'>;
  readonly label: string;
  /** Shown to the right, quieter (a state code, a requirement's state). */
  readonly hint: string;
  readonly keywords: readonly string[];
  readonly icon: IconName;
  readonly run: CommandRun;
}

/** What the palette can reach: the program in context, its target states, the latest assessment's requirements. */
export interface CommandSources {
  readonly programId: string | null;
  readonly role: Role | null;
  readonly states: readonly { code: string; name: string }[];
  readonly requirements: readonly { ref: string; title: string; jurisdiction: string }[];
}

const NO_COUNTS = { waitingForReview: 0, openChanges: 0, rulesWaiting: 0 };

/** Every command the palette offers for these sources, grouped and in display order. */
export function paletteCommands(sources: CommandSources): Command[] {
  const { programId, role } = sources;
  const screens: Command[] = [
    { id: 'go:programs', group: 'Go to', label: 'Programs', hint: '', keywords: ['home'], icon: 'phosphorBuildings', run: { kind: 'navigate', commands: ['/'] } },
    ...navLinks(programId, NO_COUNTS).map((link): Command =>
      ({ id: `go:${link.id}`, group: 'Go to', label: link.label, hint: '', keywords: [], icon: link.icon, run: { kind: 'navigate', commands: link.path } })),
  ];
  if (!programId) {
    return [...screens, ...actions(null, role)];
  }
  const review = ['/programs', programId, 'review'];
  const states = sources.states.map((s): Command => ({
    id: `state:${s.code}`, group: 'States', label: `Review ${s.name}`, hint: s.code, keywords: [s.code, s.name], icon: 'phosphorMapTrifold',
    run: { kind: 'navigate', commands: review, queryParams: { jurisdiction: s.code } },
  }));
  const requirements = sources.requirements.map((r): Command => ({
    id: `req:${r.ref}`, group: 'Requirements', label: r.title, hint: r.ref, keywords: [r.ref], icon: 'phosphorTextAlignLeft',
    run: { kind: 'navigate', commands: review, queryParams: { jurisdiction: r.jurisdiction, ref: r.ref } },
  }));
  return [...screens, ...states, ...requirements, ...actions(programId, role)];
}

/**
 * The actions a role may take from the palette, mirroring the server: anyone may start an assessment (it only
 * proposes) or download the register (the export is audited); entering a rule version is for contributors and
 * administrators.
 */
function actions(programId: string | null, role: Role | null): Command[] {
  const list: Command[] = [];
  if (programId) {
    list.push({ id: 'do:run-assessment', group: 'Actions', label: 'Run assessment', hint: '', keywords: ['assess', 'start'], icon: 'phosphorPlay', run: { kind: 'action', action: 'run-assessment' } });
    if (role === 'CONTRIBUTOR' || role === 'ADMIN') {
      list.push({ id: 'do:new-rule-version', group: 'Actions', label: 'New rule version', hint: '', keywords: ['rules', 'enter'], icon: 'phosphorPlus', run: { kind: 'action', action: 'new-rule-version' } });
    }
  }
  if (role) {
    list.push({ id: 'do:download-register', group: 'Actions', label: 'Download register', hint: 'CSV', keywords: ['export', 'csv'], icon: 'phosphorDownloadSimple', run: { kind: 'action', action: 'download-register' } });
  }
  return list;
}

export interface CommandSection { readonly group: CommandGroup; readonly commands: readonly Command[] }

/**
 * The palette's sections: recently used commands first (most recent first), then every group without them.
 * Requirements are listed only while searching, so the empty palette stays short.
 */
export function paletteSections(commands: readonly Command[], recent: readonly string[], searching: boolean): CommandSection[] {
  const byId = new Map(commands.map((c) => [c.id, c]));
  const recentCommands = recent.map((id) => byId.get(id)).filter((c): c is Command => c !== undefined);
  const recentIds = new Set(recentCommands.map((c) => c.id));
  const groups: Command['group'][] = searching ? ['Go to', 'States', 'Requirements', 'Actions'] : ['Go to', 'States', 'Actions'];
  return [
    { group: 'Recent' as const, commands: recentCommands },
    ...groups.map((group) => ({ group, commands: commands.filter((c) => c.group === group && !recentIds.has(c.id)) })),
  ].filter((section) => section.commands.length > 0);
}

export const RECENT_LIMIT = 5;

/** The recent list after using a command: it moves to the front, the list keeps at most five. */
export function pushRecent(recent: readonly string[], id: string): string[] {
  return [id, ...recent.filter((r) => r !== id)].slice(0, RECENT_LIMIT);
}

/** The palette's shortcut in words, for the key hint and the keyboard shortcuts list. */
export function paletteShortcut(platform: string): string {
  return /mac|iphone|ipad/i.test(platform) ? 'Cmd K' : 'Ctrl K';
}
