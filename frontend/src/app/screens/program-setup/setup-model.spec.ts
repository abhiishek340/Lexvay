import { describe, expect, it } from 'vitest';
import { Course, Program, ProgramDocument, ProgramFact, RequirementView, RuleVersionView } from '../../core/models';
import {
  SetupDocument, assessedVersion, currentVersion, documentState, factHistory, firstOpenStep, licenseTypes, numericFieldsUsed, programReadiness,
  quoteShowsValue,
} from './setup-model';

const TODAY = '2026-11-27';
const program: Program = { id: 'p1', name: 'Demo M.A. School Counseling (fictional)', licenseType: 'CMHC', catalogYear: '2026-27', jurisdictions: ['AZ', 'CA'], createdAt: '' };

function version(id: string, jurisdiction: string, over: Partial<RuleVersionView> = {}): RuleVersionView {
  return {
    id, jurisdiction, licenseType: 'CMHC', label: id, sourceUrl: 'https://example.invalid', legalStatus: 'IN_FORCE', effectiveFrom: '2026-07-01', effectiveTo: null,
    publishedAt: null, adoptedAt: null, datesBasis: 'Fictional demo data', observedAt: null, queuedObservations: [], supersedesId: null, documentId: 'd-' + id,
    approval: { by: 'u1', at: '2026-11-01T00:00:00Z' }, requirementCount: 2, extraction: null, createdAt: '2026-11-01T00:00:00Z', ...over,
  };
}
function requirement(ref: string, over: Partial<RequirementView> = {}): RequirementView {
  return { id: ref, ref, title: ref, text: ref, type: 'SUBJECT', numericField: null, numericOperator: null, numericValue: null, searchTerms: [], ...over };
}
const credits = requirement('AZ-1', { type: 'NUMERIC', numericField: 'TOTAL_CREDITS', numericOperator: '>=', numericValue: 60 });
const internship = requirement('AZ-5', { type: 'SUPERVISED_HOURS', numericField: 'INTERNSHIP_HOURS', numericOperator: '>=', numericValue: 600 });
const practicum = requirement('CA-5', { type: 'SUPERVISED_HOURS', numericField: 'PRACTICUM_HOURS', numericOperator: '>=', numericValue: 100 });

function course(code: string, versions: [string, string | null][]): Course {
  return { id: 'c-' + code, programId: 'p1', code, title: code, versions: versions.map(([id, supersedesId]) => ({ id, courseId: 'c-' + code, programId: 'p1', catalogYear: '2026-27', required: true, supersedesId })) };
}
function doc(id: string, courseVersionId: string | null, pageCount = 2): ProgramDocument {
  return { id, filename: id + '.pdf', kind: courseVersionId ? 'SYLLABUS' : 'DEGREE_PLAN', courseVersionId, courseCode: null, courseTitle: null, catalogYear: null, courseRequired: null, pageCount, current: true, createdAt: '' };
}
function fact(key: ProgramFact['key'], id: string, current: boolean): ProgramFact {
  return { id, programId: 'p1', key, value: 60, evidenceSpanId: 's', documentId: 'd', filename: 'plan.pdf', pageNumber: 1, quote: '60 credits', createdAt: '', createdBy: 'u', current };
}
const ready = (d: ProgramDocument): SetupDocument => ({ document: d, state: 'READY' });

describe('the rule version an assessment uses', () => {
  it('is the approved version in effect today, latest effective date first, then the newest entry', () => {
    const all = [
      version('az-old', 'AZ', { effectiveFrom: '2025-01-01' }),
      version('az-now', 'AZ'),
      version('az-next', 'AZ', { effectiveFrom: '2027-01-01' }),
      version('az-draft', 'AZ', { approval: null, effectiveFrom: '2026-08-01' }),
      version('az-other-license', 'AZ', { licenseType: 'MFT', effectiveFrom: '2026-09-01' }),
      version('az-now-later-entry', 'AZ', { createdAt: '2026-11-02T00:00:00Z' }),
    ];
    expect(assessedVersion(all, 'AZ', 'CMHC', TODAY)?.id).toBe('az-now-later-entry');
    expect(assessedVersion(all, 'AZ', 'CMHC', '2027-01-01')?.id).toBe('az-next');
    expect(assessedVersion(all, 'CA', 'CMHC', TODAY)).toBeNull();
  });

  it('is only a version legally in force: final or in force, dated, and not ended', () => {
    const all = [
      version('proposed', 'AZ', { legalStatus: 'PROPOSED' }),
      version('superseded', 'AZ', { legalStatus: 'SUPERSEDED' }),
      version('undated', 'AZ', { effectiveFrom: null }),
      version('ended', 'AZ', { effectiveTo: TODAY }),
    ];
    expect(assessedVersion(all, 'AZ', 'CMHC', TODAY)).toBeNull();
    expect(assessedVersion(all, 'AZ', 'CMHC', '2026-11-26')?.id).toBe('ended');
    expect(assessedVersion([version('final', 'AZ', { legalStatus: 'FINAL_NOT_EFFECTIVE', effectiveFrom: '2026-11-01' })], 'AZ', 'CMHC', TODAY)?.id).toBe('final');
  });

  it('offers the license types the tenant uses, once each, sorted', () => {
    expect(licenseTypes([version('a', 'AZ', { licenseType: 'MFT' }), version('b', 'CA'), version('c', 'TX')])).toEqual(['CMHC', 'MFT']);
  });
});

describe('numericFieldsUsed', () => {
  it('lists the numbers the rules engine checks, once each, in the product order', () => {
    expect(numericFieldsUsed([[internship, requirement('AZ-2'), credits], [practicum, credits]])).toEqual(['TOTAL_CREDITS', 'PRACTICUM_HOURS', 'INTERNSHIP_HOURS']);
    expect(numericFieldsUsed([[requirement('AZ-2')], []])).toEqual([]);
  });

  it('ignores a numeric field left on a requirement that is not of a numeric type', () => {
    expect(numericFieldsUsed([[requirement('X', { type: 'SUBJECT', numericField: 'TOTAL_CREDITS' })]])).toEqual([]);
  });
});

describe('courses and documents', () => {
  it("a course's current version is the one nothing supersedes", () => {
    expect(currentVersion(course('COUN 625', [['v1', null], ['v2', 'v1']]))?.id).toBe('v2');
    expect(currentVersion(course('COUN 605', [['v1', null]]))?.id).toBe('v1');
  });

  it('a document is ready with pages, reading while its job runs or is not known yet, failed when the job failed or left no page', () => {
    expect(documentState(doc('a', null), undefined)).toBe('READY');
    expect(documentState(doc('b', null, 0), undefined)).toBe('READING');
    expect(documentState(doc('b', null, 0), { jobId: 'j', status: 'RUNNING', error: null })).toBe('READING');
    expect(documentState(doc('b', null, 0), { jobId: 'j', status: 'FAILED', error: 'Unreadable' })).toBe('FAILED');
    expect(documentState(doc('b', null, 0), { jobId: 'j', status: 'DONE', error: null })).toBe('FAILED');
  });
});

describe('programReadiness', () => {
  const rules = [version('az', 'AZ'), version('ca', 'CA')];
  const requirements = new Map<string, RequirementView[]>([['az', [credits, internship]], ['ca', [credits, requirement('CA-2')]]]);
  const courses = [course('COUN 605', [['v605', null]]), course('COUN 625', [['v625', null], ['v625b', 'v625']])];
  const documents = [ready(doc('plan', null)), ready(doc('s605', 'v605')), ready(doc('s625', 'v625b'))];
  const facts = [fact('TOTAL_CREDITS', 'f1', true), fact('INTERNSHIP_HOURS', 'f2', true)];

  it('is ready when every check passes', () => {
    const r = programReadiness(program, courses, documents, facts, rules, requirements, TODAY);
    expect(r.ready).toBe(true);
    expect(r.checks.map((c) => [c.id, c.ok, c.text])).toEqual([
      ['RULES', true, 'Every state has approved rules'],
      ['COURSE_DOCUMENTS', true, 'Every course has a ready document'],
      ['READING', true, 'No document is still being read'],
      ['FACTS', true, 'Every number the rules check is recorded'],
    ]);
    expect(r.fieldsUsed).toEqual(['TOTAL_CREDITS', 'INTERNSHIP_HOURS']);
  });

  it('blocks on a state without approved rules in effect, naming it', () => {
    const r = programReadiness(program, courses, documents, facts, [version('az', 'AZ'), version('ca', 'CA', { effectiveFrom: '2027-01-01' })], requirements, TODAY);
    expect(r.ready).toBe(false);
    expect(r.checks[0]).toMatchObject({ ok: false, blocking: true, items: ['CA'], text: 'No approved rules for CA' });
  });

  it("blocks on a course whose current version has no ready document; an earlier version's syllabus does not count", () => {
    const r = programReadiness(program, courses, [ready(doc('s605', 'v605')), ready(doc('old625', 'v625'))], facts, rules, requirements, TODAY);
    expect(r.ready).toBe(false);
    expect(r.checks[1]).toMatchObject({ ok: false, blocking: true, items: ['COUN 625'], text: 'No ready document for COUN 625' });
  });

  it('blocks while a document is still being read; a failed one does not block', () => {
    const reading = programReadiness(program, courses, [...documents, { document: doc('new', null, 0), state: 'READING' }], facts, rules, requirements, TODAY);
    expect(reading.ready).toBe(false);
    expect(reading.checks[2]).toMatchObject({ ok: false, text: '1 document is still being read' });
    const failed = programReadiness(program, courses, [...documents, { document: doc('bad', null, 0), state: 'FAILED' }], facts, rules, requirements, TODAY);
    expect(failed.ready).toBe(true);
  });

  it('warns about a missing fact without blocking, and says what the rules engine will do', () => {
    const r = programReadiness(program, courses, documents, [fact('TOTAL_CREDITS', 'f1', true)], rules, requirements, TODAY);
    expect(r.ready).toBe(true);
    expect(r.checks[3]).toMatchObject({
      ok: false, blocking: false, items: ['Internship hours'],
      text: 'Internship hours not recorded: the rules engine will report it as missing evidence',
    });
  });

  it('warns, without blocking, when the program has no courses yet', () => {
    const r = programReadiness(program, [], [ready(doc('plan', null))], facts, rules, requirements, TODAY);
    expect(r.checks[1]).toMatchObject({ ok: false, blocking: false, text: 'No courses yet' });
    expect(r.ready).toBe(true);
  });

  it('starts at the first step that needs work', () => {
    const r = (d: SetupDocument[], f: ProgramFact[]) => programReadiness(program, courses, d, f, rules, requirements, TODAY);
    expect(firstOpenStep([], [], r([], []))).toBe('COURSES');
    expect(firstOpenStep(courses, [], r([], []))).toBe('DOCUMENTS');
    expect(firstOpenStep(courses, documents, r(documents, []))).toBe('FACTS');
    expect(firstOpenStep(courses, documents, r(documents, facts))).toBe('READINESS');
  });
});

describe('facts', () => {
  it('warns when the selected passage does not show the number as written', () => {
    expect(quoteShowsValue(60, 'The program requires the completion of 60 semester credit hours.')).toBe(true);
    expect(quoteShowsValue(1000, 'at least 1,000 clock hours')).toBe(true);
    expect(quoteShowsValue(60.5, 'a total of 60.5 credits')).toBe(true);
    expect(quoteShowsValue(60, 'sixty semester credit hours')).toBe(false);
    expect(quoteShowsValue(60, 'COUN 605 and 610')).toBe(false);
    expect(quoteShowsValue(null, 'anything')).toBe(true);
  });

  it('shows the newest fact per field with the earlier ones as history', () => {
    const facts = [fact('TOTAL_CREDITS', 'new', true), fact('PRACTICUM_HOURS', 'p', true), fact('TOTAL_CREDITS', 'old', false)];
    expect(factHistory(facts, 'TOTAL_CREDITS')).toMatchObject({ current: { id: 'new' }, earlier: [{ id: 'old' }] });
    expect(factHistory(facts, 'INTERNSHIP_HOURS')).toEqual({ current: null, earlier: [] });
  });
});
