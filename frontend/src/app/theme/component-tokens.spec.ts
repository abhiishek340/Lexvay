import { updatePreset } from '@primeuix/themes';
import { describe, expect, it, vi } from 'vitest';
import { registerComponentTokens } from './component-tokens';

vi.mock('@primeuix/themes', () => ({ updatePreset: vi.fn() }));

describe('registerComponentTokens', () => {
  it('adds each component to the preset once, however many chunks name it', () => {
    registerComponentTokens({ alpha: { root: { a: 1 } }, beta: { root: { b: 2 } } });
    registerComponentTokens({ beta: { root: { b: 2 } }, gamma: { root: { c: 3 } } });
    registerComponentTokens({ alpha: { root: { a: 1 } } });

    expect(vi.mocked(updatePreset).mock.calls).toEqual([
      [{ components: { alpha: { root: { a: 1 } }, beta: { root: { b: 2 } } } }],
      [{ components: { gamma: { root: { c: 3 } } } }],
    ]);
  });
});
