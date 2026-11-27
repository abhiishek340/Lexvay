import { describe, expect, it } from 'vitest';
import { RULE_STATUS } from '../screens/rules/rule-status';
import { RuleVersionView } from './models';
import { anyExtracting, comparableVersions, groupRuleVersions, openingVersion, ruleVersionStatus, versionsWaitingForApproval } from './rules';

let seq = 0;
function version(patch: Partial<RuleVersionView> = {}): RuleVersionView {
  seq++;
  return {
    id: `rv-${seq}`, jurisdiction: 'CO', licenseType: 'LPC', label: `Version ${seq}`, sourceUrl: 'https://example.invalid', effectiveFrom: '2026-07-01',
    supersedesId: null, documentId: `doc-${seq}`, approval: null, requirementCount: 0, extraction: null, createdAt: `2026-11-0${seq % 9 + 1}T00:00:00Z`,
    ...patch,
  };
}
const job = (status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED', error: string | null = null) => ({ jobId: 'job', status, error });
const approval = { by: 'user-1', at: '2026-11-26T10:00:00Z' };

describe('ruleVersionStatus', () => {
  it('is Extracting while the extraction job is queued or running and no list exists', () => {
    for (const status of ['QUEUED', 'RUNNING'] as const) {
      const v = version({ extraction: job(status) });
      expect(ruleVersionStatus(v, [v])).toBe('EXTRACTING');
    }
  });

  it('is Extraction failed when the job failed and nobody entered a list', () => {
    const v = version({ extraction: job('FAILED', 'model output invalid twice') });
    expect(ruleVersionStatus(v, [v])).toBe('EXTRACTION_FAILED');
  });

  it('is Needs approval once requirements exist, including a list entered by hand after a failed extraction', () => {
    const extracted = version({ extraction: job('DONE'), requirementCount: 9 });
    const entered = version({ extraction: job('FAILED', 'x'), requirementCount: 2 });
    expect(ruleVersionStatus(extracted, [extracted, entered])).toBe('NEEDS_APPROVAL');
    expect(ruleVersionStatus(entered, [extracted, entered])).toBe('NEEDS_APPROVAL');
  });

  it('is Approved once approved, and Superseded when another version replaces it', () => {
    const old = version({ approval, requirementCount: 9, extraction: job('DONE') });
    const revision = version({ approval, requirementCount: 9, extraction: job('DONE'), supersedesId: old.id });
    expect(ruleVersionStatus(old, [old])).toBe('APPROVED');
    expect(ruleVersionStatus(old, [old, revision])).toBe('SUPERSEDED');
    expect(ruleVersionStatus(revision, [old, revision])).toBe('APPROVED');
  });

  it('marks a version Superseded as soon as a newer version names it, even before that one is approved', () => {
    const old = version({ approval, requirementCount: 9 });
    const draft = version({ supersedesId: old.id, extraction: job('RUNNING') });
    expect(ruleVersionStatus(old, [old, draft])).toBe('SUPERSEDED');
    expect(ruleVersionStatus(draft, [old, draft])).toBe('EXTRACTING');
  });

  it('every status has its own word', () => {
    expect(new Set(Object.values(RULE_STATUS).map((s) => s.label)).size).toBe(5);
  });
});

describe('versionsWaitingForApproval and anyExtracting', () => {
  it('counts unapproved versions that have requirements and are not superseded', () => {
    const waiting = version({ requirementCount: 3 });
    const approved = version({ approval, requirementCount: 3 });
    const extracting = version({ extraction: job('RUNNING') });
    const failed = version({ extraction: job('FAILED') });
    const replacedDraft = version({ requirementCount: 3 });
    const replacement = version({ supersedesId: replacedDraft.id, requirementCount: 2 });
    const all = [waiting, approved, extracting, failed, replacedDraft, replacement];
    expect(versionsWaitingForApproval(all)).toBe(2);
    expect(anyExtracting(all)).toBe(true);
    expect(anyExtracting([waiting, approved, failed])).toBe(false);
  });
});

describe('groupRuleVersions', () => {
  const names: Record<string, string> = { CO: 'Colorado', UT: 'Utah', NV: 'Nevada', AL: 'Alabama' };
  const name = (code: string) => names[code] ?? code;

  it('puts the program jurisdictions first in program order, then the others by code', () => {
    const all = [version({ jurisdiction: 'NV' }), version({ jurisdiction: 'CO' }), version({ jurisdiction: 'AL' }), version({ jurisdiction: 'UT' })];
    const groups = groupRuleVersions(all, ['UT', 'CO', 'AZ'], name);
    expect(groups.map((g) => g.code)).toEqual(['UT', 'CO', 'AL', 'NV']);
    expect(groups.map((g) => g.target)).toEqual([true, true, false, false]);
    expect(groups[0].name).toBe('Utah');
  });

  it('orders a group newest effective date first, then newest entry, and derives each status', () => {
    const original = version({ effectiveFrom: '2026-07-01', approval, requirementCount: 9, createdAt: '2026-11-01T00:00:00Z' });
    const revision = version({ effectiveFrom: '2027-01-01', supersedesId: original.id, approval, requirementCount: 9, createdAt: '2026-11-02T00:00:00Z' });
    const sameDayLater = version({ effectiveFrom: '2026-07-01', requirementCount: 1, createdAt: '2026-11-03T00:00:00Z' });
    const [group] = groupRuleVersions([original, sameDayLater, revision], ['CO'], name);
    expect(group.rows.map((r) => r.view.id)).toEqual([revision.id, sameDayLater.id, original.id]);
    expect(group.rows.map((r) => r.status)).toEqual(['APPROVED', 'NEEDS_APPROVAL', 'SUPERSEDED']);
  });
});

describe('openingVersion', () => {
  const name = (code: string) => code;

  it('opens the first version that waits for a person, in list order', () => {
    const approved = version({ jurisdiction: 'UT', approval, requirementCount: 9 });
    const failed = version({ jurisdiction: 'NV', extraction: job('FAILED', 'no script') });
    const needs = version({ jurisdiction: 'OR', requirementCount: 2 });
    expect(openingVersion(groupRuleVersions([needs, approved, failed], ['UT'], name))).toBe(failed.id);
  });

  it('otherwise opens the newest approved version of the first state, never a superseded one', () => {
    const original = version({ jurisdiction: 'CO', effectiveFrom: '2026-07-01', approval, requirementCount: 9 });
    const revision = version({ jurisdiction: 'CO', effectiveFrom: '2027-01-01', supersedesId: original.id, approval, requirementCount: 9 });
    const utah = version({ jurisdiction: 'UT', approval, requirementCount: 9 });
    expect(openingVersion(groupRuleVersions([original, revision, utah], ['CO', 'UT'], name))).toBe(revision.id);
  });

  it('opens nothing for a tenant without versions, and an extracting one when it is all there is', () => {
    expect(openingVersion([])).toBeNull();
    const extracting = version({ extraction: job('RUNNING') });
    expect(openingVersion(groupRuleVersions([extracting], [], name))).toBe(extracting.id);
  });
});

describe('comparableVersions', () => {
  it('offers approved versions of the same jurisdiction and license type, newest first, never the version itself', () => {
    const a = version({ approval, effectiveFrom: '2026-01-01' });
    const b = version({ approval, effectiveFrom: '2027-01-01' });
    const unapproved = version();
    const otherState = version({ approval, jurisdiction: 'UT' });
    const otherLicense = version({ approval, licenseType: 'LMFT' });
    const all = [a, b, unapproved, otherState, otherLicense];
    expect(comparableVersions(all, 'CO', 'LPC', b.id).map((v) => v.id)).toEqual([a.id]);
    expect(comparableVersions(all, 'CO', 'LPC', null).map((v) => v.id)).toEqual([b.id, a.id]);
  });
});
