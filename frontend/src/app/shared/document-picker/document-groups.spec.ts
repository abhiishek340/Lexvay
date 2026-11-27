import { describe, expect, it } from 'vitest';
import { ProgramDocument } from '../../core/models';
import { PROGRAM_DOCUMENTS, documentGroups, hasEarlierDocuments } from './document-groups';

function doc(id: string, over: Partial<ProgramDocument>): ProgramDocument {
  return {
    id, filename: id + '.pdf', kind: 'SYLLABUS', courseVersionId: null, courseCode: null, courseTitle: null, catalogYear: null,
    courseRequired: null, pageCount: 2, current: true, createdAt: '2026-11-01T00:00:00Z', ...over,
  };
}

describe('documentGroups: the document picker', () => {
  // The server's order: program-level documents first, then course code, newest catalog year first.
  const documents = [
    doc('plan', { kind: 'DEGREE_PLAN' }),
    doc('handbook', { kind: 'HANDBOOK', pageCount: 0 }),
    doc('coun605-2027', { courseCode: 'COUN 605', courseTitle: 'Ethics', catalogYear: '2027-28', courseVersionId: 'v2' }),
    doc('coun605-2026', { courseCode: 'COUN 605', courseTitle: 'Ethics', catalogYear: '2026-27', courseVersionId: 'v1', current: false }),
    doc('coun650', { courseCode: 'COUN 650', courseTitle: 'Addictions', catalogYear: '2026-27', courseVersionId: 'v3' }),
  ];

  it('groups current documents by course with program documents first', () => {
    const groups = documentGroups(documents, false);
    expect(groups.map((g) => g.label)).toEqual([PROGRAM_DOCUMENTS, 'COUN 605 Ethics', 'COUN 650 Addictions']);
    expect(groups[1].items.map((d) => d.id)).toEqual(['coun605-2027']);
    expect(groups[0].items[0]).toEqual({ id: 'plan', label: 'plan.pdf, degree plan', filename: 'plan.pdf', kind: 'degree plan', catalogYear: null, disabled: false });
  });

  it('disables a document without text and says so in its option text', () => {
    const handbook = documentGroups(documents, false)[0].items[1];
    expect(handbook.disabled).toBe(true);
    expect(handbook.label).toBe('handbook.pdf, handbook, No text available yet');
  });

  it('adds earlier catalog years on request, labelled with their year', () => {
    const course = documentGroups(documents, true)[1];
    expect(course.items.map((d) => d.id)).toEqual(['coun605-2027', 'coun605-2026']);
    expect(course.items[1].catalogYear).toBe('2026-27');
    expect(course.items[1].label).toBe('coun605-2026.pdf, syllabus, catalog year 2026-27');
    expect(course.items[0].catalogYear).toBeNull();
    expect(hasEarlierDocuments(documents)).toBe(true);
    expect(hasEarlierDocuments(documents.filter((d) => d.current))).toBe(false);
  });

  it('has no program group when every document belongs to a course', () => {
    expect(documentGroups(documents.slice(2), false).map((g) => g.label)).toEqual(['COUN 605 Ethics', 'COUN 650 Addictions']);
    expect(documentGroups([], true)).toEqual([]);
  });
});
