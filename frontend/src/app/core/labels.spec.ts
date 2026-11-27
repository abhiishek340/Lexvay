import { describe, expect, it } from 'vitest';
import { LEXVAY_ICONS } from '../theme/icons';
import { ACTION_LABEL, CHANGE_KIND, DECISION_ICON, DECISION_LABEL, FINDING_ICON, FINDING_LABEL, OUTCOME_LABEL, ROLE_LABEL, actionLabel, humanize, licenseLabel } from './labels';

describe('labels', () => {
  it('never use the forbidden phrases', () => {
    const all = [...Object.values(FINDING_LABEL), ...Object.values(DECISION_LABEL), ...Object.values(OUTCOME_LABEL), ...Object.values(ACTION_LABEL),
      ...Object.values(ROLE_LABEL), ...Object.values(CHANGE_KIND).flatMap((k) => [k.label, k.action])].join(' ').toLowerCase();
    for (const forbidden of [['compl', 'iant'].join(''), ['meets requ', 'irements'].join('')]) {
      expect(all).not.toContain(forbidden);
    }
  });

  it('every finding and decision has a distinct, registered icon so state is not colour alone', () => {
    expect(new Set(Object.values(FINDING_ICON)).size).toBe(3);
    expect(new Set(Object.values(DECISION_ICON)).size).toBe(3);
    for (const name of [...Object.values(FINDING_ICON), ...Object.values(DECISION_ICON), ...Object.values(CHANGE_KIND).map((k) => k.icon)]) {
      expect(LEXVAY_ICONS[name]).toContain('<svg');
    }
  });

  it('unknown actions and keys fall back to readable text', () => {
    expect(actionLabel('SOMETHING_NEW')).toBe('something new');
    expect(humanize('requirementRef')).toBe('requirement ref');
    expect(licenseLabel({ licenseType: 'CLINICAL_MENTAL_HEALTH_COUNSELING' })).toBe('Clinical mental health counseling license');
  });
});
