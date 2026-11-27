import { describe, expect, it } from 'vitest';
import { DecisionKind, Finding } from '../core/models';
import { requirementStatus } from './status';

describe('requirementStatus', () => {
  it('outlines the proposed finding while nobody has decided', () => {
    expect(requirementStatus('EVIDENCE_FOUND', null, false)).toEqual({ icon: 'phosphorCheckCircle', tone: 'found', label: 'Evidence found, not reviewed' });
    expect(requirementStatus('AMBIGUOUS', null, false)).toEqual({ icon: 'phosphorQuestion', tone: 'ambiguous', label: 'Ambiguous, not reviewed' });
    expect(requirementStatus('EVIDENCE_MISSING', null, false)).toEqual({ icon: 'phosphorXCircle', tone: 'missing', label: 'Evidence missing, not reviewed' });
  });

  it('fills the decision once it has landed, keeping the finding in the words', () => {
    expect(requirementStatus('EVIDENCE_FOUND', 'ACCEPTED', false)).toEqual({ icon: 'phosphorCheckCircleFill', tone: 'found', label: 'Evidence found, accepted' });
    expect(requirementStatus('AMBIGUOUS', 'EVIDENCE_REQUESTED', false)).toEqual({ icon: 'phosphorPaperPlaneTiltFill', tone: 'ambiguous', label: 'Ambiguous, evidence requested' });
    expect(requirementStatus('EVIDENCE_MISSING', 'GAP_CONFIRMED', false)).toEqual({ icon: 'phosphorXCircleFill', tone: 'missing', label: 'Evidence missing, gap confirmed' });
  });

  it('says outdated for a stale mapping whatever was decided', () => {
    for (const decision of [null, 'ACCEPTED', 'GAP_CONFIRMED'] as (DecisionKind | null)[]) {
      expect(requirementStatus('EVIDENCE_FOUND', decision, true)).toEqual({ icon: 'phosphorArrowsClockwise', tone: 'muted', label: 'Evidence found, outdated by a change' });
    }
  });

  it('gives every combination a distinct label', () => {
    const findings: Finding[] = ['EVIDENCE_FOUND', 'AMBIGUOUS', 'EVIDENCE_MISSING'];
    const decisions: (DecisionKind | null)[] = [null, 'ACCEPTED', 'EVIDENCE_REQUESTED', 'GAP_CONFIRMED'];
    const labels = findings.flatMap((f) => decisions.map((d) => requirementStatus(f, d, false).label));
    expect(new Set(labels).size).toBe(labels.length);
  });
});
