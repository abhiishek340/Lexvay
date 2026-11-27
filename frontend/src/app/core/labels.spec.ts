import { describe, expect, it } from 'vitest';
import { CHANGE_KIND } from '../screens/changes/change-kind';
import { DIFF_STATUS } from '../screens/rules/diff-status';
import { RULE_STATUS } from '../screens/rules/rule-status';
import { DECISION_ICON, FINDING_ICON } from '../ui/finding';
import {
  ACTION_LABEL, DECISION_LABEL, FINDING_LABEL, NUMERIC_FIELD_LABEL, OUTCOME_LABEL, REQUIREMENT_TYPE_LABEL,
  ROLE_LABEL, actionLabel, humanize, licenseLabel, numericCheck,
} from './labels';

describe('labels', () => {
  it('never use the forbidden phrases', () => {
    const all = [...Object.values(FINDING_LABEL), ...Object.values(DECISION_LABEL), ...Object.values(OUTCOME_LABEL), ...Object.values(ACTION_LABEL),
      ...Object.values(ROLE_LABEL), ...Object.values(CHANGE_KIND).flatMap((k) => [k.label, k.action]), ...Object.values(REQUIREMENT_TYPE_LABEL),
      ...Object.values(NUMERIC_FIELD_LABEL), ...Object.values(DIFF_STATUS).map((s) => s.label), ...Object.values(RULE_STATUS).map((s) => s.label)].join(' ').toLowerCase();
    for (const forbidden of [['compl', 'iant'].join(''), ['meets requ', 'irements'].join('')]) {
      expect(all).not.toContain(forbidden);
    }
  });

  it('every finding and decision has a distinct icon so state is not colour alone (icons.spec.ts checks each is registered)', () => {
    expect(new Set(Object.values(FINDING_ICON)).size).toBe(3);
    expect(new Set(Object.values(DECISION_ICON)).size).toBe(3);
  });

  it('every diff status has its own icon, and a numeric check reads as field, operator and value', () => {
    expect(new Set(Object.values(DIFF_STATUS).map((s) => s.icon)).size).toBe(4);
    expect(numericCheck({ numericField: 'INTERNSHIP_HOURS', numericOperator: '>=', numericValue: 700 })).toBe('Internship hours >= 700');
    expect(numericCheck({ numericField: null, numericOperator: null, numericValue: null })).toBeNull();
  });

  it('unknown actions and keys fall back to readable text', () => {
    expect(actionLabel('SOMETHING_NEW')).toBe('something new');
    expect(humanize('requirementRef')).toBe('requirement ref');
    expect(licenseLabel({ licenseType: 'CLINICAL_MENTAL_HEALTH_COUNSELING' })).toBe('Clinical mental health counseling license');
  });
});
