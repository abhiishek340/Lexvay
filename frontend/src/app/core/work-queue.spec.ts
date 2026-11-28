import { describe, expect, it } from 'vitest';
import { WorkItem } from './models';
import { workItemView } from './work-queue';

const names: Record<string, string> = { CA: 'California', CO: 'Colorado' };
const name = (code: string) => names[code] ?? code;
const item = (over: Partial<WorkItem>): WorkItem => ({
  kind: 'REQUIREMENTS_TO_REVIEW', programId: 'p1', programName: 'M.S. Counseling', jurisdictionCode: 'CA', count: 8, oldestAt: '2026-11-01T00:00:00Z', ids: {}, ...over,
});

describe('workItemView', () => {
  it('writes the count before the noun and opens Review on the state', () => {
    expect(workItemView(item({}), null, name)).toEqual({
      title: '8 to review', detail: 'California · M.S. Counseling',
      commands: ['/programs', 'p1', 'review'], queryParams: { jurisdiction: 'CA' },
    });
    expect(workItemView(item({ kind: 'EVIDENCE_REQUESTS_OPEN', count: 1 }), null, name)).toMatchObject({ title: '1 evidence request', commands: ['/programs', 'p1', 'requests'] });
    expect(workItemView(item({ kind: 'READY_TO_SIGN', count: 1 }), null, name)).toMatchObject({ title: 'Ready to sign', queryParams: { jurisdiction: 'CA' } });
  });

  it('opens a rule version to approve in the program being worked on', () => {
    const rule = item({ kind: 'RULE_VERSIONS_TO_APPROVE', programId: null, programName: null, jurisdictionCode: 'CO', count: 2, ids: { ruleVersionIds: ['rv1', 'rv2'] } });
    expect(workItemView(rule, 'p9', name)).toEqual({
      title: '2 rule versions to approve', detail: 'Colorado', commands: ['/programs', 'p9', 'rules', 'rv1'],
    });
    expect(workItemView(rule, null, name).commands).toEqual(['/']);
  });

  it('opens open changes in the first program they touch', () => {
    const change = item({ kind: 'CHANGES_OPEN', programId: null, programName: null, jurisdictionCode: null, count: 1, ids: { changeKind: 'COURSE_VERSION', programIds: ['p2'] } });
    expect(workItemView(change, 'p9', name)).toEqual({ title: '1 open change', detail: 'New course versions', commands: ['/programs', 'p2', 'changes'] });
  });

  it('opens a failed assessment on its program overview', () => {
    const failed = item({ kind: 'ASSESSMENTS_FAILED', jurisdictionCode: null, count: 2 });
    expect(workItemView(failed, null, name)).toMatchObject({ title: '2 failed assessments', detail: 'M.S. Counseling', commands: ['/programs', 'p1', 'overview'] });
  });

  it('counts failed extractions', () => {
    expect(workItemView(item({ kind: 'EXTRACTION_FAILED', count: 1, programId: null }), 'p1', name).title).toBe('1 extraction failed');
  });
});
