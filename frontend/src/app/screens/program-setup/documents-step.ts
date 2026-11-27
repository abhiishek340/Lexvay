import { Component, computed, inject, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon } from '@ng-icons/core';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { FileSelectEvent, FileUpload } from 'primeng/fileupload';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { Tag } from 'primeng/tag';
import { humanize } from '../../core/labels';
import { Course, DocumentKind, ProgramDocument } from '../../core/models';
import { DocumentViewer } from '../../shared/document-viewer/document-viewer';
import { Sheet } from '../../shared/sheet/sheet';
import { Enter } from '../../ui/enter';
import { DocumentState, currentVersion } from './setup-model';
import { SetupNotice, SetupStore } from './setup-store';
import { UploadEntry, UploadState } from './upload-queue';

/** The kinds a program document can have; rule text is entered through rule versions (D-009). */
const KINDS: DocumentKind[] = ['SYLLABUS', 'DEGREE_PLAN', 'HANDBOOK', 'ACCREDITATION'];

/** A course's documents (the current catalog year's first) or the program's own, as the step lists them. */
interface DocumentSection {
  key: string;
  label: string;
  code: string | null;
  documents: { document: ProgramDocument; state: DocumentState; current: boolean }[];
}

/** One word and one shape per upload state; the Tag colour only repeats them. */
const UPLOAD_STATE: Record<UploadState, { label: string; severity: 'secondary' | 'info' | 'success' | 'danger' | 'warn' }> = {
  WAITING: { label: 'Waiting', severity: 'secondary' },
  UPLOADING: { label: 'Uploading', severity: 'info' },
  READING: { label: 'Reading', severity: 'info' },
  READY: { label: 'Ready', severity: 'success' },
  FAILED: { label: 'Failed', severity: 'danger' },
  DUPLICATE: { label: 'Already uploaded', severity: 'warn' },
};
const DOCUMENT_STATE: Record<DocumentState, { label: string; severity: 'info' | 'success' | 'danger' }> = {
  READING: { label: 'Reading', severity: 'info' },
  READY: { label: 'Ready', severity: 'success' },
  FAILED: { label: 'Not readable', severity: 'danger' },
};

/**
 * The Documents step: the program's documents grouped like F4's picker (program documents, then each course), a drop
 * zone that takes files into a course or into the program, a row per chosen file with its kind (and course version for
 * a syllabus) that follows it from Uploading to Reading to Ready or Failed, and each document readable in a sheet.
 * Accepted types and the size limit are the server's: its words appear on the file's row.
 */
@Component({
  selector: 'lx-setup-documents',
  imports: [FormsModule, NgIcon, ButtonDirective, ButtonLabel, FileUpload, Select, Skeleton, Tag, DocumentViewer, Sheet, Enter],
  templateUrl: './documents-step.html',
  styleUrl: './steps.css',
})
export class DocumentsStep {
  readonly store = inject(SetupStore);
  readonly announce = output<SetupNotice>();
  private readonly uploader = viewChild<FileUpload>('uploader');

  readonly uploadState = UPLOAD_STATE;
  readonly documentState = DOCUMENT_STATE;
  readonly kinds = KINDS.map((value) => ({ value, label: capitalised(humanize(value)) }));
  readonly kindLabel = (kind: string): string => capitalised(humanize(kind));

  /** Where the next chosen files go: a course id, or '' for the program's own documents. */
  readonly target = signal('');
  readonly targets = computed(() => [
    { value: '', label: 'Program documents' },
    ...(this.store.courses() ?? []).map((c) => ({ value: c.id, label: `${c.code} ${c.title}` })),
  ]);
  /** Every course version as "COUN 605, 2026-27", for a syllabus row. */
  readonly versions = computed(() => (this.store.courses() ?? []).flatMap((c) => c.versions.map((v) => ({ value: v.id, label: `${c.code}, ${v.catalogYear}` }))));

  readonly sections = computed<DocumentSection[] | null>(() => {
    const courses = this.store.courses();
    if (!courses || this.store.documents() === null) { return null; }
    const documents = this.store.setupDocuments();
    const own = documents.filter((d) => d.document.courseVersionId === null).map((d) => ({ ...d, current: true }));
    return [
      { key: 'program', label: 'Program documents', code: null, documents: own },
      ...courses.map((course) => this.section(course, documents)),
    ];
  });

  /** The document open in the reading sheet. */
  readonly reading = signal<{ id: string; filename: string } | null>(null);
  private opener: HTMLElement | null = null;

  chosen(event: FileSelectEvent): void {
    const course = (this.store.courses() ?? []).find((c) => c.id === this.target());
    this.store.queue.add([...event.files], { courseVersionId: course ? (currentVersion(course)?.id ?? null) : null });
    // The rows are the queue's; PrimeNG's own list would show every file twice.
    this.uploader()?.clear();
  }

  async upload(): Promise<void> {
    const count = this.store.queue.waiting().length;
    await this.store.uploadWaiting();
    this.announce.emit({ summary: `${count} ${count === 1 ? 'file' : 'files'} sent`, detail: 'Reading starts at once.' });
  }

  /** "Ready (2 pages)": the state's word, with the page count once read. */
  uploadLabel(entry: UploadEntry): string {
    const label = UPLOAD_STATE[entry.state].label;
    return entry.state === 'READY' ? `${label} (${entry.pageCount} ${entry.pageCount === 1 ? 'page' : 'pages'})` : label;
  }

  size(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  open(id: string, filename: string, trigger: EventTarget | null): void {
    this.opener = trigger instanceof HTMLElement ? trigger.closest('button') : null;
    this.reading.set({ id, filename });
  }

  close(): void {
    this.reading.set(null);
    const opener = this.opener;
    this.opener = null;
    opener?.focus();
  }

  /** The file name of a document the program already holds (a duplicate's row links to it). */
  filenameOf(id: string): string {
    return this.store.documents()?.find((d) => d.id === id)?.filename ?? 'the existing document';
  }

  private section(course: Course, documents: readonly { document: ProgramDocument; state: DocumentState }[]): DocumentSection {
    const current = currentVersion(course)?.id;
    const ids = new Set(course.versions.map((v) => v.id));
    const own = documents.filter((d) => d.document.courseVersionId && ids.has(d.document.courseVersionId))
      .map((d) => ({ ...d, current: d.document.courseVersionId === current }))
      .sort((a, b) => Number(b.current) - Number(a.current));
    return { key: course.id, label: course.title, code: course.code, documents: own };
  }
}

function capitalised(words: string): string {
  return words.charAt(0).toUpperCase() + words.slice(1);
}
