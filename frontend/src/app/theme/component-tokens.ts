import { updatePreset } from '@primeuix/themes';

type ComponentTokens = Record<string, unknown>;

const registered = new Set<string>();

/**
 * Adds the token sets of PrimeNG components to the active preset. The shared preset (lexvay-preset.ts) carries only
 * what the shell and first paint need; a screen, or a shared component, registers the sets of the components only it
 * renders from its own chunk, before the first of them is created. A component already registered is skipped, so
 * two chunks that render the same component may both name it.
 */
export function registerComponentTokens(sets: Record<string, ComponentTokens>): void {
  const fresh = Object.entries(sets).filter(([name]) => !registered.has(name));
  if (fresh.length === 0) { return; }
  fresh.forEach(([name]) => registered.add(name));
  updatePreset({ components: Object.fromEntries(fresh) });
}
