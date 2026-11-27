import { describe, expect, it } from 'vitest';
import { DocumentView } from '../../core/models';
import { UploadQueue } from './upload-queue';

const file = (name: string): File => new File(['fictional'], name, { type: 'application/pdf' });
function view(id: string, status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED', pages = 0, error: string | null = null): DocumentView {
  return {
    id, programId: 'p1', courseVersionId: null, filename: id, kind: 'SYLLABUS', createdAt: '',
    pages: Array.from({ length: pages }, (_, i) => ({ pageNumber: i + 1 })), ingest: { jobId: 'j-' + id, status, error },
  };
}

describe('UploadQueue', () => {
  it('takes files into a course as syllabi of its version, and into the program as degree plans', () => {
    const q = new UploadQueue();
    q.add([file('a.pdf'), file('b.docx')], { courseVersionId: 'v605' });
    q.add([file('plan.pdf')], { courseVersionId: null });
    expect(q.entries().map((e) => [e.file.name, e.kind, e.courseVersionId, e.state])).toEqual([
      ['a.pdf', 'SYLLABUS', 'v605', 'WAITING'], ['b.docx', 'SYLLABUS', 'v605', 'WAITING'], ['plan.pdf', 'DEGREE_PLAN', null, 'WAITING'],
    ]);
  });

  it('lets a waiting file change its kind and course version; only a syllabus keeps a course version', () => {
    const q = new UploadQueue();
    q.add([file('a.pdf')], { courseVersionId: 'v605' });
    const key = q.entries()[0].key;
    q.choose(key, { courseVersionId: 'v610' });
    expect(q.entries()[0]).toMatchObject({ kind: 'SYLLABUS', courseVersionId: 'v610' });
    q.choose(key, { kind: 'HANDBOOK' });
    expect(q.entries()[0]).toMatchObject({ kind: 'HANDBOOK', courseVersionId: null });
    q.started(key);
    q.choose(key, { kind: 'SYLLABUS' });
    expect(q.entries()[0].kind).toBe('HANDBOOK');
  });

  it('sends one file at a time, in the order chosen', () => {
    const q = new UploadQueue();
    q.add([file('a.pdf'), file('b.pdf')], { courseVersionId: null });
    const [a, b] = q.entries().map((e) => e.key);
    expect(q.next()?.key).toBe(a);
    q.started(a);
    expect(q.next()).toBeNull();
    q.uploaded(a, 'doc-a');
    expect(q.next()?.key).toBe(b);
    expect(q.reading().map((e) => e.documentId)).toEqual(['doc-a']);
  });

  it('follows a reading to Ready with its page count, or to Failed with the job error', () => {
    const q = new UploadQueue();
    q.add([file('a.pdf'), file('b.pdf')], { courseVersionId: null });
    const [a, b] = q.entries().map((e) => e.key);
    q.started(a); q.uploaded(a, 'doc-a');
    q.started(b); q.uploaded(b, 'doc-b');
    q.read(view('doc-a', 'RUNNING'));
    expect(q.entries()[0].state).toBe('READING');
    q.read(view('doc-a', 'DONE', 2));
    q.read(view('doc-b', 'FAILED', 0, 'The PDF has no readable text'));
    expect(q.entries().map((e) => [e.state, e.pageCount, e.message])).toEqual([['READY', 2, null], ['FAILED', 0, 'The PDF has no readable text']]);
    expect(q.reading()).toEqual([]);
  });

  it('shows a duplicate as already uploaded with the existing document, and any other refusal in the server words', () => {
    const q = new UploadQueue();
    q.add([file('a.pdf'), file('b.exe')], { courseVersionId: null });
    const [a, b] = q.entries().map((e) => e.key);
    q.started(a);
    q.refused(a, { status: 409, detail: 'A document with the same content was already uploaded: d1', existingDocumentId: 'd1' });
    q.started(b);
    q.refused(b, { status: 400, detail: 'Unsupported file type', existingDocumentId: null });
    expect(q.entries().map((e) => [e.state, e.existingDocumentId, e.message])).toEqual([['DUPLICATE', 'd1', null], ['FAILED', null, 'Unsupported file type']]);
  });

  it('removes a waiting or finished file but never one in flight', () => {
    const q = new UploadQueue();
    q.add([file('a.pdf'), file('b.pdf'), file('c.pdf')], { courseVersionId: null });
    const [a, b, c] = q.entries().map((e) => e.key);
    q.started(b);
    q.started(c); q.uploaded(c, 'doc-c');
    q.remove(a); q.remove(b); q.remove(c);
    expect(q.entries().map((e) => e.key)).toEqual([b, c]);
  });
});
