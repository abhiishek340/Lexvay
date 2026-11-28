import { describe, expect, it } from 'vitest';
import { MAX_RULE_TEXT, emptyForm, formProblems, problemField, toNewRuleVersion } from './rule-version-form';

const filled = {
  ...emptyForm('CLINICAL_MENTAL_HEALTH_COUNSELING'),
  jurisdiction: 'NV', label: '  Demo rule set - Nevada  ', sourceUrl: 'https://example.invalid/nv', legalStatus: 'IN_FORCE' as const, effectiveFrom: '2027-01-01', datesBasis: ' Fictional notice 4 ', text: 'Rule 1. Degree.',
};

describe('rule version form', () => {
  it('names every missing field before anything is sent', () => {
    expect(Object.keys(formProblems(emptyForm('')))).toEqual(['jurisdiction', 'licenseType', 'label', 'sourceUrl', 'legalStatus', 'effectiveFrom', 'datesBasis', 'text']);
    expect(formProblems(filled)).toEqual({});
  });

  it('checks the URL scheme and the text limit the server enforces', () => {
    expect(formProblems({ ...filled, sourceUrl: 'example.invalid/nv' }).sourceUrl).toContain('https://');
    expect(formProblems({ ...filled, text: 'x'.repeat(MAX_RULE_TEXT) })).toEqual({});
    expect(formProblems({ ...filled, text: 'x'.repeat(MAX_RULE_TEXT + 1) }).text).toContain('500 000');
  });

  it('sends trimmed fields and the text exactly as pasted', () => {
    expect(toNewRuleVersion({ ...filled, text: ' Rule 1.\n' })).toEqual({
      jurisdiction: 'NV', licenseType: 'CLINICAL_MENTAL_HEALTH_COUNSELING', label: 'Demo rule set - Nevada', sourceUrl: 'https://example.invalid/nv',
      legalStatus: 'IN_FORCE', effectiveFrom: '2027-01-01', datesBasis: 'Fictional notice 4', supersedesId: null, text: ' Rule 1.\n',
    });
  });

  it('maps the server problem details onto the field they are about', () => {
    expect(problemField('Unknown jurisdiction code: ZZ')).toBe('jurisdiction');
    expect(problemField('A rule version may only supersede one for the same jurisdiction and license type')).toBe('supersedesId');
    expect(problemField('Superseded rule version not found')).toBe('supersedesId');
    expect(problemField('A document with the same content was already uploaded: 7f1c')).toBe('text');
    expect(problemField('Say where the dates come from: a date needs its basis')).toBe('datesBasis');
    expect(problemField('Invalid request content.')).toBeNull();
  });
});
