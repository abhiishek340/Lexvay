import { describe, expect, it } from 'vitest';
import { Role } from '../../core/models';
import { CommandSources, paletteCommands, paletteSections, pushRecent } from './commands';
import { paletteShortcut } from './shortcut';

const sources = (role: Role | null, programId: string | null = 'p1'): CommandSources => ({
  programId,
  role,
  states: [{ code: 'CA', name: 'California' }, { code: 'TX', name: 'Texas' }],
  requirements: [{ ref: 'CA-3', title: 'Telehealth competence', jurisdiction: 'CA' }],
});
const ids = (role: Role | null, programId: string | null = 'p1') => paletteCommands(sources(role, programId)).map((c) => c.id);

describe('paletteCommands', () => {
  it('goes to every screen of the program and the tenant-wide ones', () => {
    expect(ids('REVIEWER').filter((id) => id.startsWith('go:'))).toEqual(
      ['go:programs', 'go:register', 'go:overview', 'go:review', 'go:requests', 'go:changes', 'go:rules', 'go:report', 'go:audit']);
  });

  it('jumps to Review for a state and for a requirement by ref', () => {
    const commands = paletteCommands(sources('REVIEWER'));
    expect(commands.find((c) => c.id === 'state:TX')).toMatchObject({
      label: 'Review Texas', hint: 'TX', run: { kind: 'navigate', commands: ['/programs', 'p1', 'review'], queryParams: { jurisdiction: 'TX' } },
    });
    expect(commands.find((c) => c.id === 'req:CA-3')).toMatchObject({
      label: 'Telehealth competence', keywords: ['CA-3'], run: { queryParams: { jurisdiction: 'CA', ref: 'CA-3' } },
    });
  });

  it('offers only the actions the role may take', () => {
    const actions = (role: Role) => ids(role).filter((id) => id.startsWith('do:'));
    expect(actions('CONTRIBUTOR')).toEqual(['do:run-assessment', 'do:new-rule-version', 'do:download-register']);
    expect(actions('ADMIN')).toEqual(['do:run-assessment', 'do:new-rule-version', 'do:download-register']);
    expect(actions('REVIEWER')).toEqual(['do:run-assessment', 'do:download-register']);
    expect(actions('SIGNER')).toEqual(['do:run-assessment', 'do:download-register']);
  });

  it('without a program offers the tenant-wide screens and the register download only', () => {
    expect(ids('SIGNER', null)).toEqual(['go:programs', 'go:register', 'do:download-register']);
  });
});

describe('paletteSections', () => {
  const commands = paletteCommands(sources('REVIEWER'));

  it('puts recent commands first and does not repeat them in their groups', () => {
    const sections = paletteSections(commands, ['state:TX', 'go:audit'], false);
    expect(sections[0]).toMatchObject({ group: 'Recent' });
    expect(sections[0].commands.map((c) => c.id)).toEqual(['state:TX', 'go:audit']);
    const rest = sections.slice(1).flatMap((s) => s.commands.map((c) => c.id));
    expect(rest).not.toContain('state:TX');
    expect(rest).not.toContain('go:audit');
  });

  it('lists requirements only while searching', () => {
    expect(paletteSections(commands, [], false).map((s) => s.group)).toEqual(['Go to', 'States', 'Actions']);
    expect(paletteSections(commands, [], true).map((s) => s.group)).toEqual(['Go to', 'States', 'Requirements', 'Actions']);
  });

  it('drops recent ids that no longer exist', () => {
    expect(paletteSections(commands, ['state:ZZ'], false)[0].group).toBe('Go to');
  });
});

describe('recent commands and the shortcut', () => {
  it('moves a used command to the front and keeps five', () => {
    expect(pushRecent(['a', 'b', 'c'], 'b')).toEqual(['b', 'a', 'c']);
    expect(pushRecent(['a', 'b', 'c', 'd', 'e'], 'f')).toEqual(['f', 'a', 'b', 'c', 'd']);
  });

  it('names Cmd K on Apple platforms and Ctrl K elsewhere', () => {
    expect(paletteShortcut('MacIntel')).toBe('Cmd K');
    expect(paletteShortcut('Win32')).toBe('Ctrl K');
    expect(paletteShortcut('Linux x86_64')).toBe('Ctrl K');
  });
});
