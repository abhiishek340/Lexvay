import { describe, expect, it } from 'vitest';
import { downloadsNote } from './report-downloads';

describe('downloadsNote', () => {
  it('explains in one line why neither download exists before a signature, or after a reopening', () => {
    expect(downloadsNote(null, false)).toBe('PDF and package after signing');
    expect(downloadsNote('REOPENED', true)).toBe('Reopened · PDF and package after signing again');
  });

  it('asks for the PDF before the package of a signed determination, then says nothing', () => {
    expect(downloadsNote('SIGNED', false)).toBe('Package after the PDF');
    expect(downloadsNote('SIGNED', true)).toBeNull();
  });
});
