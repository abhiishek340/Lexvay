import { describe, expect, it } from 'vitest';
import { leaveNeedsConfirmation } from './unsaved-changes';

const here = '/programs/p1/rules/rv-1';

describe('leaveNeedsConfirmation', () => {
  it('never asks when nothing is unsaved', () => {
    expect(leaveNeedsConfirmation(false, here, '/programs/p1/rules/rv-2')).toBe(false);
    expect(leaveNeedsConfirmation(false, here, '/programs/p1/audit')).toBe(false);
  });

  it('asks before another version, another screen or signing out loses unsaved edits', () => {
    expect(leaveNeedsConfirmation(true, here, '/programs/p1/rules/rv-2')).toBe(true);
    expect(leaveNeedsConfirmation(true, here, '/programs/p1/rules')).toBe(true);
    expect(leaveNeedsConfirmation(true, here, '/programs/p1/audit')).toBe(true);
    expect(leaveNeedsConfirmation(true, here, '/login')).toBe(true);
  });

  it('does not ask when the navigation stays on the same version', () => {
    expect(leaveNeedsConfirmation(true, here, `${here}?tab=compare`)).toBe(false);
    expect(leaveNeedsConfirmation(true, here, `${here}/`)).toBe(false);
    expect(leaveNeedsConfirmation(true, here, `${here}#top`)).toBe(false);
  });
});
