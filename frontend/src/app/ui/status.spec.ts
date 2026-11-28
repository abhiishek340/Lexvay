import { describe, expect, it } from 'vitest';
import { DecisionKind, Finding } from '../core/models';
import { decisionStatus, requirementStatus } from './status';

describe('requirementStatus', () => {
  it('shows the proposed finding while nobody has decided', () => {
    expect(requirementStatus('EVIDENCE_FOUND', null, false)).toEqual({ status: 'EVIDENCE_FOUND', label: 'Evidence found, not reviewed' });
    expect(requirementStatus('AMBIGUOUS', null, false)).toEqual({ status: 'AMBIGUOUS', label: 'Ambiguous, not reviewed' });
    expect(requirementStatus('EVIDENCE_MISSING', null, false)).toEqual({ status: 'EVIDENCE_MISSING', label: 'Evidence missing, not reviewed' });
  });

  it('shows the decision once it has landed, keeping the finding in the words', () => {
    expect(requirementStatus('EVIDENCE_FOUND', 'ACCEPTED', false)).toEqual({ status: 'ACCEPTED', label: 'Evidence found, accepted' });
    expect(requirementStatus('AMBIGUOUS', 'EVIDENCE_REQUESTED', false)).toEqual({ status: 'EVIDENCE_REQUESTED', label: 'Ambiguous, evidence requested' });
    expect(requirementStatus('EVIDENCE_MISSING', 'GAP_CONFIRMED', false)).toEqual({ status: 'GAP_CONFIRMED', label: 'Evidence missing, gap confirmed' });
  });

  it('says outdated for a stale mapping whatever was decided', () => {
    for (const decision of [null, 'ACCEPTED', 'GAP_CONFIRMED'] as (DecisionKind | null)[]) {
      expect(requirementStatus('EVIDENCE_FOUND', decision, true)).toEqual({ status: 'OUTDATED', label: 'Evidence found, outdated by a change' });
    }
  });

  it('gives every combination a distinct label', () => {
    const findings: Finding[] = ['EVIDENCE_FOUND', 'AMBIGUOUS', 'EVIDENCE_MISSING'];
    const decisions: (DecisionKind | null)[] = [null, 'ACCEPTED', 'EVIDENCE_REQUESTED', 'GAP_CONFIRMED'];
    const labels = findings.flatMap((f) => decisions.map((d) => requirementStatus(f, d, false).label));
    expect(new Set(labels).size).toBe(labels.length);
  });
});

describe('decisionStatus', () => {
  it('names the decision, an outdated one, or none', () => {
    expect(decisionStatus('ACCEPTED', false)).toEqual({ status: 'ACCEPTED', label: 'Accepted' });
    expect(decisionStatus(null, false)).toEqual({ status: 'NOT_REVIEWED', label: 'Not reviewed' });
    expect(decisionStatus('GAP_CONFIRMED', true)).toEqual({ status: 'OUTDATED', label: 'Outdated: gap confirmed' });
    expect(decisionStatus(null, true)).toEqual({ status: 'OUTDATED', label: 'Outdated' });
  });
});
