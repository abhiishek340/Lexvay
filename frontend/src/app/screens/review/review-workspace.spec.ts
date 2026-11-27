import { describe, expect, it } from 'vitest';
import { Decision, Evidence, EvidenceResponse, Finding, Mapping } from '../../core/models';
import { ReviewWorkspace, decisionBlocker, evidenceView, isDecided, shortcutFor } from './review-workspace';

const decision: Decision = { kind: 'ACCEPTED', note: null, userId: 'u', userDisplayName: 'Dana', at: '2026-11-26T10:00:00Z' };

function mapping(id: string, finding: Finding, decided = false, stale = false): Mapping {
  return {
    id, assessmentId: 'a', jurisdictionId: 'j', jurisdiction: 'CA', ruleVersionId: 'r',
    requirement: { id: 'req-' + id, ref: 'CA-' + id, title: 'T' + id, text: '', type: 'SUBJECT', numericField: null, numericOperator: null, numericValue: null },
    finding, method: 'MATCH', rationale: '', stale, evidence: null, decision: decided ? decision : null, responseCount: 0,
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

const systemEvidence: Evidence = {
  spanId: 's1', documentId: 'd1', filename: 'coun605.pdf', kind: 'SYLLABUS', courseVersionId: 'cv', courseCode: 'COUN 605', courseRequired: true,
  pageNumber: 1, charStart: 10, charEnd: 40, quote: 'the ACA Code of Ethics and law', textSource: 'TEXT_LAYER', ocrConfidence: null,
};

function response(id: string, createdAt: string): EvidenceResponse {
  return {
    id, reviewEventId: 'e' + id, documentId: 'd2', filename: 'handbook.pdf', kind: 'HANDBOOK', courseCode: null, courseRequired: null,
    pageNumber: 2, charStart: 5, charEnd: 25, quote: 'Week 3: duty to warn', comment: null, providedBy: 'u2', providedByName: 'Jordan Ellis', createdAt,
  };
}

describe('evidenceView: responses merged into the Evidence pane', () => {
  it('keeps the system evidence first and unchanged and adds the provided passages in the server order', () => {
    const m = { ...mapping('1', 'AMBIGUOUS'), evidence: systemEvidence, responseCount: 2 };
    const provided = [response('r2', '2026-11-26T12:00:00Z'), response('r1', '2026-11-25T12:00:00Z')];
    const view = evidenceView(m, new Map([['1', provided]]));
    expect(view.system).toBe(systemEvidence);
    expect(view.provided.map((r) => r.id)).toEqual(['r2', 'r1']);
    expect(view.providedLoading).toBe(false);
    expect(view.answered).toBe(true);
  });

  it('is loading while responses the mapping counts are not fetched, or more arrived since', () => {
    const m = { ...mapping('1', 'EVIDENCE_MISSING'), responseCount: 2 };
    expect(evidenceView(m, new Map()).providedLoading).toBe(true);
    expect(evidenceView(m, new Map([['1', [response('r1', '2026-11-25T12:00:00Z')]]])).providedLoading).toBe(true);
  });

  it('asks for nothing and says nothing was answered when no person provided evidence', () => {
    const view = evidenceView(mapping('1', 'EVIDENCE_MISSING'), new Map());
    expect(view).toEqual({ system: null, provided: [], providedLoading: false, answered: false });
  });

  it('is no longer an answered request once a reviewer decided, but the provided passage stays', () => {
    const m = { ...mapping('1', 'EVIDENCE_MISSING', true), responseCount: 1 };
    const view = evidenceView(m, new Map([['1', [response('r1', '2026-11-25T12:00:00Z')]]]));
    expect(view.answered).toBe(false);
    expect(view.provided).toHaveLength(1);
  });

  it('follows the selection and the cache of the workspace', () => {
    const w = workspace();
    w.mappings.update((list) => list.map((m) => (m.id === '2' ? { ...m, responseCount: 1 } : m)));
    expect(w.evidence()?.providedLoading).toBe(true);
    w.setResponses('2', [response('r1', '2026-11-25T12:00:00Z')]);
    expect(w.evidence()?.provided.map((r) => r.id)).toEqual(['r1']);
    expect(w.evidence()?.providedLoading).toBe(false);
    w.select('3');
    expect(w.evidence()?.provided).toEqual([]);
  });
});
