import { describe, expect, it } from 'vitest';
import { Decision, Finding, Mapping } from '../../core/models';
import { ReviewWorkspace, decisionBlocker, isDecided, shortcutFor } from './review-workspace';

const decision: Decision = { kind: 'ACCEPTED', note: null, userId: 'u', userDisplayName: 'Dana', at: '2026-11-26T10:00:00Z' };

function mapping(id: string, finding: Finding, decided = false, stale = false): Mapping {
  return {
    id, assessmentId: 'a', jurisdictionId: 'j', jurisdiction: 'CA', ruleVersionId: 'r',
    requirement: { id: 'req-' + id, ref: 'CA-' + id, title: 'T' + id, text: '', type: 'TOPIC', numericField: null, numericOperator: null, numericValue: null },
    finding, method: 'MATCH', rationale: '', stale, evidence: null, decision: decided ? decision : null,
  };
}

function workspace(): ReviewWorkspace {
  const w = new ReviewWorkspace();
  w.load([
    mapping('1', 'EVIDENCE_FOUND', true),
    mapping('2', 'AMBIGUOUS'),
    mapping('3', 'EVIDENCE_FOUND'),
    mapping('4', 'EVIDENCE_MISSING'),
    mapping('5', 'EVIDENCE_FOUND', true, true),
  ]);
  return w;
}

describe('ReviewWorkspace progress', () => {
  it('counts only current decisions; a stale decision needs review again', () => {
    const w = workspace();
    expect(isDecided(w.mappings()[4])).toBe(false);
    expect(w.decided()).toBe(1);
    expect(w.progressLabel()).toBe('1 of 5 reviewed');
    expect(w.allDecided()).toBe(false);
  });

  it('counts every filter chip', () => {
    expect(workspace().counts()).toEqual({ ALL: 5, NEEDS_REVIEW: 4, EVIDENCE_FOUND: 3, AMBIGUOUS: 1, EVIDENCE_MISSING: 1 });
  });

  it('is never all decided when the list is empty', () => {
    const w = new ReviewWorkspace();
    w.load([]);
    expect(w.allDecided()).toBe(false);
    expect(w.selected()).toBeNull();
  });
});

describe('ReviewWorkspace selection and filters', () => {
  it('opens at the first requirement that needs a decision', () => {
    expect(workspace().selectedId()).toBe('2');
  });

  it('J and K move within the visible list and stop at the ends', () => {
    const w = workspace();
    expect(w.move(1)?.id).toBe('3');
    w.move(1); w.move(1); w.move(1);
    expect(w.selectedId()).toBe('5');
    w.move(-1);
    expect(w.selectedId()).toBe('4');
    w.select('1');
    expect(w.move(-1)?.id).toBe('1');
  });

  it('keeps the selection when the filter still shows it, otherwise takes the first visible row', () => {
    const w = workspace();
    w.select('3');
    w.setFilter('EVIDENCE_FOUND');
    expect(w.selectedId()).toBe('3');
    w.setFilter('EVIDENCE_MISSING');
    expect(w.selectedId()).toBe('4');
    expect(w.visible().map((m) => m.id)).toEqual(['4']);
    w.setFilter('NEEDS_REVIEW');
    expect(w.visible().map((m) => m.id)).toEqual(['2', '3', '4', '5']);
  });

  it('ignores selection of an id that is not in the list', () => {
    const w = workspace();
    w.select('nope');
    expect(w.selectedId()).toBe('2');
  });
});

describe('ReviewWorkspace after a decision', () => {
  it('advances to the next requirement that needs a decision, wrapping around', () => {
    const w = workspace();
    w.select('4');
    const next = w.applyDecision({ ...w.selected()!, decision });
    expect(next?.id).toBe('5');
    expect(w.decided()).toBe(2);
    w.applyDecision({ ...w.selected()!, stale: false, decision });
    expect(w.selectedId()).toBe('2');
  });

  it('advances in list order even when the filter hides the decided row', () => {
    const w = workspace();
    w.setFilter('NEEDS_REVIEW');
    w.select('3');
    w.applyDecision({ ...w.selected()!, decision });
    expect(w.selectedId()).toBe('4');
    expect(w.visible().map((m) => m.id)).toEqual(['2', '4', '5']);
  });

  it('stays on the decided requirement when nothing is left', () => {
    const w = new ReviewWorkspace();
    w.load([mapping('1', 'EVIDENCE_FOUND')]);
    expect(w.applyDecision({ ...w.selected()!, decision })?.id).toBe('1');
    expect(w.allDecided()).toBe(true);
    expect(w.progressLabel()).toBe('1 of 1 reviewed');
  });
});

describe('keyboard shortcuts', () => {
  it('maps J, K, A, R, G and ? to commands in either case', () => {
    expect(['j', 'K', 'a', 'R', 'g', '?'].map((key) => shortcutFor({ key }))).toEqual(['next', 'previous', 'accept', 'request', 'gap', 'help']);
  });

  it('never fires while typing or with a modifier held', () => {
    expect(shortcutFor({ key: 'a', targetTag: 'TEXTAREA' })).toBeNull();
    expect(shortcutFor({ key: 'a', targetTag: 'input' })).toBeNull();
    expect(shortcutFor({ key: 'a', targetTag: 'DIV', targetEditable: true })).toBeNull();
    expect(shortcutFor({ key: 'a', ctrlKey: true })).toBeNull();
    expect(shortcutFor({ key: 'j', metaKey: true })).toBeNull();
    expect(shortcutFor({ key: 'Enter' })).toBeNull();
    expect(shortcutFor({ key: 'x' })).toBeNull();
  });
});

describe('decision rules', () => {
  it('a gap needs a note; the other decisions do not', () => {
    expect(decisionBlocker('GAP_CONFIRMED', '   ')).toContain('requires a note');
    expect(decisionBlocker('GAP_CONFIRMED', 'No telehealth content.')).toBeNull();
    expect(decisionBlocker('ACCEPTED', '')).toBeNull();
    expect(decisionBlocker('EVIDENCE_REQUESTED', '')).toBeNull();
  });
});
