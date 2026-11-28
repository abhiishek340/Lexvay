import { describe, expect, it } from 'vitest';
import { ACTION_LABEL } from '../screens/audit/audit-sentence';
import { CHANGE_KIND } from '../screens/changes/change-kind';
import { DIFF_STATUS } from '../screens/rules/diff-status';
import { RULE_STATUS } from '../screens/rules/rule-status';
import {
  DECISION_LABEL, FINDING_LABEL, NUMERIC_FIELD_LABEL, OUTCOME_LABEL, REQUIREMENT_TYPE_LABEL,
  ROLE_LABEL, humanize, licenseLabel, numericCheck,
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

  it('every diff status has its own word, and a numeric check reads as field, operator and value', () => {
    expect(new Set(Object.values(DIFF_STATUS).map((s) => s.label)).size).toBe(4);
    expect(numericCheck({ numericField: 'INTERNSHIP_HOURS', numericOperator: '>=', numericValue: 700 })).toBe('Internship hours >= 700');
    expect(numericCheck({ numericField: null, numericOperator: null, numericValue: null })).toBeNull();
  });

  it('unknown keys fall back to readable text', () => {
    expect(humanize('requirementRef')).toBe('requirement ref');
    expect(licenseLabel({ licenseType: 'CLINICAL_MENTAL_HEALTH_COUNSELING' })).toBe('Clinical mental health counseling license');
  });
});
