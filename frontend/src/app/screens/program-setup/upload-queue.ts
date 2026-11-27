import { computed, signal } from '@angular/core';
import { DocumentKind, DocumentView } from '../../core/models';

/**
 * One chosen file on its way into the program: waiting for "Upload", uploading, being read by the ingest job, ready
 * with its pages, failed with the server's words, or refused as a copy of a document the program already has.
 */
export type UploadState = 'WAITING' | 'UPLOADING' | 'READING' | 'READY' | 'FAILED' | 'DUPLICATE';

export interface UploadEntry {
  readonly key: number;
  readonly file: File;
  readonly kind: DocumentKind;
  /** The course version a syllabus belongs to; null for program documents. */
  readonly courseVersionId: string | null;
  readonly state: UploadState;
  readonly documentId: string | null;
  readonly pageCount: number;
  /** The server's message for a failure. */
  readonly message: string | null;
  /** For a duplicate: the document the program already holds with the same content. */
  readonly existingDocumentId: string | null;
}

/** Where chosen files go: into a course (its current version) as syllabi, or into the program's own documents. */
export interface UploadTarget { readonly courseVersionId: string | null }

/** Why the server refused an upload, as the queue needs it. */
export interface UploadRefusal { readonly status: number; readonly detail: string; readonly existingDocumentId: string | null }

/**
 * The files of the Documents step from choice to ready (F2 point 4). Plain signals, no Angular dependencies, so the
 * whole lifecycle is unit-tested. Files are uploaded one at a time, in the order chosen (`next`), and each upload's
 * reading is then followed through GET /documents/{id} (`read`).
 */
export class UploadQueue {
  private nextKey = 1;
  readonly entries = signal<readonly UploadEntry[]>([]);
  readonly waiting = computed(() => this.entries().filter((e) => e.state === 'WAITING'));
  readonly reading = computed(() => this.entries().filter((e) => e.state === 'READING'));
  readonly busy = computed(() => this.entries().some((e) => e.state === 'UPLOADING'));

  /** A syllabus when it goes into a course, a degree plan when it goes into the program; both can be changed per file. */
  add(files: readonly File[], target: UploadTarget): void {
    const added = files.map((file): UploadEntry => ({
      key: this.nextKey++, file, kind: target.courseVersionId ? 'SYLLABUS' : 'DEGREE_PLAN', courseVersionId: target.courseVersionId,
      state: 'WAITING', documentId: null, pageCount: 0, message: null, existingDocumentId: null,
    }));
    this.entries.update((entries) => [...entries, ...added]);
  }

  /** Only a waiting file can change; a course version travels with a syllabus only. */
  choose(key: number, choice: { kind?: DocumentKind; courseVersionId?: string | null }): void {
    this.patch(key, (e) => {
      if (e.state !== 'WAITING') { return e; }
      const kind = choice.kind ?? e.kind;
      const courseVersionId = kind === 'SYLLABUS' ? (choice.courseVersionId !== undefined ? choice.courseVersionId : e.courseVersionId) : null;
      return { ...e, kind, courseVersionId };
    });
  }

  /** A file still waiting, or one that is finished either way, leaves the list; an upload or a reading in progress stays. */
  remove(key: number): void {
    this.entries.update((entries) => entries.filter((e) => e.key !== key || e.state === 'UPLOADING' || e.state === 'READING'));
  }

  /** The next file to send, or null while one is being sent or nothing waits. */
  next(): UploadEntry | null {
    return this.busy() ? null : (this.waiting()[0] ?? null);
  }

  started(key: number): void {
    this.patch(key, (e) => ({ ...e, state: 'UPLOADING', message: null }));
  }

  uploaded(key: number, documentId: string): void {
    this.patch(key, (e) => ({ ...e, state: 'READING', documentId }));
  }

  /** A 409 that names the existing document is a duplicate; anything else fails with the server's words. */
  refused(key: number, refusal: UploadRefusal): void {
    this.patch(key, (e) => (refusal.status === 409 && refusal.existingDocumentId
      ? { ...e, state: 'DUPLICATE', existingDocumentId: refusal.existingDocumentId, message: null }
      : { ...e, state: 'FAILED', message: refusal.detail }));
  }

  /** The document as GET /documents/{id} sees it: ready once its job is done, failed with the job's error. */
  read(view: DocumentView): void {
    const entry = this.entries().find((e) => e.documentId === view.id && e.state === 'READING');
    if (!entry || !view.ingest) { return; }
    if (view.ingest.status === 'DONE') {
      this.patch(entry.key, (e) => ({ ...e, state: 'READY', pageCount: view.pages.length }));
    } else if (view.ingest.status === 'FAILED') {
      this.patch(entry.key, (e) => ({ ...e, state: 'FAILED', message: view.ingest?.error ?? 'The document could not be read.' }));
    }
  }

  private patch(key: number, change: (entry: UploadEntry) => UploadEntry): void {
    this.entries.update((entries) => entries.map((e) => (e.key === key ? change(e) : e)));
  }
}
