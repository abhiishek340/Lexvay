import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import {
  AssessmentView, Course, DocumentView, Jurisdiction, NewCourse, NewCourseVersion, NewProgram, NewProgramFact, Program, ProgramDocument, ProgramFact,
  RequirementView, RuleVersionView,
} from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { SessionService } from '../../core/session.service';
import { Ingest, SetupDocument, assessedVersions, documentState, programReadiness } from './setup-model';
import { UploadQueue } from './upload-queue';

/** Reading status and a running assessment are fetched again this often, and only while the tab is visible. */
const POLL_MS = 2000;

/** Today as the server compares effective dates (yyyy-MM-dd, local calendar day). */
function localToday(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

/** What a step just did: a toast on screen, and the sentence for the hidden live region. */
export interface SetupNotice { readonly summary: string; readonly detail: string; readonly severity?: 'success' | 'error' }

/** A started assessment as the Readiness step follows it: requirements processed out of the requirements it will assess. */
export interface AssessmentRun { readonly view: AssessmentView; readonly processed: number; readonly total: number }

/**
 * Everything the setup workspace shows, for one program, in one place: the screen provides it and every step reads
 * it, so a course added in one step is the course the next step uploads into, and readiness is always computed from
 * what the other steps hold. Reads go through ApiService; the program itself is ProgramContext's.
 */
@Injectable()
export class SetupStore {
  private readonly api = inject(ApiService);
  private readonly context = inject(ProgramContext);
  private readonly session = inject(SessionService);

  /** The program being set up; null on /programs/new until it is created. */
  readonly programId = signal<string | null>(null);
  readonly program = computed<Program | null>(() => {
    const program = this.context.program();
    return program && program.id === this.programId() ? program : null;
  });
  readonly jurisdictions = signal<Jurisdiction[]>([]);
  readonly ruleVersions = signal<RuleVersionView[]>([]);
  readonly courses = signal<Course[] | null>(null);
  readonly documents = signal<ProgramDocument[] | null>(null);
  readonly facts = signal<ProgramFact[] | null>(null);
  /** The ingest job of each document that had no pages when listed, from GET /documents/{id}. */
  readonly ingest = signal<ReadonlyMap<string, Ingest>>(new Map());
  /** The requirement list of each rule version an assessment would use, by version id. */
  readonly requirements = signal<ReadonlyMap<string, RequirementView[]>>(new Map());
  readonly queue = new UploadQueue();
  readonly run = signal<AssessmentRun | null>(null);
  readonly error = signal<string | null>(null);
  readonly today = localToday();

  readonly canEdit = computed(() => this.session.canSetUp());
  /** Starting an assessment, as F2 lists it: contributors, administrators and reviewers. */
  readonly canRun = computed(() => ['CONTRIBUTOR', 'ADMIN', 'REVIEWER'].includes(this.session.user()?.role ?? ''));
  readonly loaded = computed(() => this.program() !== null && this.courses() !== null && this.documents() !== null && this.facts() !== null);

  readonly setupDocuments = computed<SetupDocument[]>(() =>
    (this.documents() ?? []).map((document) => ({ document, state: documentState(document, this.ingest().get(document.id)) })));
  readonly assessed = computed(() => {
    const program = this.program();
    return program ? assessedVersions(program, this.ruleVersions(), this.today) : [];
  });
  readonly readiness = computed(() => {
    const program = this.program();
    return program
      ? programReadiness(program, this.courses() ?? [], this.setupDocuments(), this.facts() ?? [], this.ruleVersions(), this.requirements(), this.today)
      : null;
  });
  /** Documents still being read: listed without pages and not failed, or uploaded here and not finished. */
  private readonly readingIds = computed(() => [...new Set([
    ...this.setupDocuments().filter((d) => d.state === 'READING').map((d) => d.document.id),
    ...this.queue.reading().map((e) => e.documentId!),
  ])]);
  private readonly visible = signal(typeof document === 'undefined' || document.visibilityState === 'visible');

  constructor() {
    Promise.all([firstValueFrom(this.api.jurisdictions()), firstValueFrom(this.api.ruleVersions())])
      .then(([jurisdictions, versions]) => { this.jurisdictions.set(jurisdictions); this.ruleVersions.set(versions); })
      .catch((e: unknown) => this.error.set(problemDetail(e, 'The states and rule versions could not be loaded.')));

    effect(() => {
      const id = this.programId();
      untracked(() => this.load(id));
    });
    // The requirement lists of the versions the run would use: the numbers they check decide the Program facts rows.
    effect(() => {
      const missing = this.assessed().filter((v) => !this.requirements().has(v.id));
      if (missing.length === 0) { return; }
      untracked(() => Promise.all(missing.map((v) => firstValueFrom(this.api.requirements(v.id))))
        .then((views) => this.requirements.update((known) => new Map([...known, ...views.map((r): [string, RequirementView[]] => [r.ruleVersionId, r.requirements])])))
        .catch((e: unknown) => this.error.set(problemDetail(e, 'The requirement lists could not be loaded.'))));
    });
    // While anything is being read, ask after it every two seconds (never while the tab is hidden).
    effect((onCleanup) => {
      const ids = this.readingIds();
      if (ids.length === 0 || !this.visible()) { return; }
      const timer = setTimeout(() => this.followReading(ids), POLL_MS);
      onCleanup(() => clearTimeout(timer));
    });
    effect((onCleanup) => {
      const run = this.run();
      if (!run || !this.visible() || (run.view.status !== 'QUEUED' && run.view.status !== 'RUNNING')) { return; }
      const timer = setTimeout(() => this.followRun(run.view.id).catch((e: unknown) => this.error.set(problemDetail(e, 'The assessment could not be followed.'))), POLL_MS);
      onCleanup(() => clearTimeout(timer));
    });
    const onVisibility = (): void => this.visible.set(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', onVisibility);
    inject(DestroyRef).onDestroy(() => document.removeEventListener('visibilitychange', onVisibility));
  }

  /** POST /programs; the screen then moves to the program's own setup URL. */
  async create(body: NewProgram): Promise<Program> {
    return firstValueFrom(this.api.createProgram(body));
  }

  async addCourse(body: NewCourse): Promise<Course> {
    const course = await firstValueFrom(this.api.createCourse(this.requireId(), body));
    await this.refreshCourses();
    return course;
  }

  /** A new catalog year supersedes the course's current version; it opens a change event for every assessment that cited it. */
  async addVersion(courseId: string, body: NewCourseVersion): Promise<Course> {
    const course = await firstValueFrom(this.api.addCourseVersion(courseId, body));
    await this.refreshCourses();
    return course;
  }

  /** Sends the waiting files one after the other; each then waits for its reading in the queue. */
  async uploadWaiting(): Promise<void> {
    const programId = this.requireId();
    for (let entry = this.queue.next(); entry; entry = this.queue.next()) {
      this.queue.started(entry.key);
      try {
        const result = await firstValueFrom(this.api.uploadDocument(programId, entry.file, entry.kind, entry.courseVersionId));
        this.queue.uploaded(entry.key, result.documentId);
      } catch (e: unknown) {
        const error = e instanceof HttpErrorResponse ? e : null;
        const existing = (error?.error as { existingDocumentId?: unknown } | null)?.existingDocumentId;
        this.queue.refused(entry.key, {
          status: error?.status ?? 0, detail: problemDetail(e, 'The file could not be uploaded.'), existingDocumentId: typeof existing === 'string' ? existing : null,
        });
      }
    }
    await this.refreshDocuments();
  }

  async recordFact(body: NewProgramFact): Promise<ProgramFact> {
    const fact = await firstValueFrom(this.api.recordFact(this.requireId(), body));
    this.facts.set(await firstValueFrom(this.api.facts(this.requireId())));
    return fact;
  }

  /** 202: the run proposes findings; the Readiness step follows it until it finishes or fails. */
  async startAssessment(): Promise<void> {
    const started = await firstValueFrom(this.api.startAssessment(this.requireId()));
    await this.followRun(started.assessmentId);
  }

  private async load(programId: string | null): Promise<void> {
    this.courses.set(null);
    this.documents.set(null);
    this.facts.set(null);
    this.ingest.set(new Map());
    this.run.set(null);
    if (!programId) { return; }
    try {
      const [courses, documents, facts] = await Promise.all([
        firstValueFrom(this.api.courses(programId)), firstValueFrom(this.api.programDocuments(programId)), firstValueFrom(this.api.facts(programId)),
      ]);
      if (this.programId() !== programId) { return; }
      this.courses.set(courses);
      this.documents.set(documents);
      this.facts.set(facts);
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The program setup could not be loaded.'));
    }
  }

  private async refreshCourses(): Promise<void> {
    this.courses.set(await firstValueFrom(this.api.courses(this.requireId())));
  }

  private async refreshDocuments(): Promise<void> {
    this.documents.set(await firstValueFrom(this.api.programDocuments(this.requireId())));
  }

  private async followReading(ids: readonly string[]): Promise<void> {
    try {
      const views = await Promise.all(ids.map((id) => firstValueFrom(this.api.document(id))));
      views.forEach((v: DocumentView) => this.queue.read(v));
      this.ingest.update((known) => new Map([...known, ...views.map((v): [string, Ingest] => [v.id, v.ingest])]));
      if (views.some((v) => v.pages.length > 0 || v.ingest?.status === 'FAILED' || v.ingest?.status === 'DONE')) {
        await this.refreshDocuments();
      }
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The reading status could not be loaded.'));
    }
  }

  private async followRun(assessmentId: string): Promise<void> {
    const view = await firstValueFrom(this.api.assessment(assessmentId));
    const processed = view.jurisdictions.reduce((sum, j) => sum + j.total, 0);
    const total = this.assessed().reduce((sum, v) => sum + (this.requirements().get(v.id)?.length ?? v.requirementCount), 0);
    this.run.set({ view, processed, total: Math.max(total, processed) });
    if (view.status === 'FINISHED') {
      await this.context.refreshAfterChange();
    }
  }

  private requireId(): string {
    const id = this.programId();
    if (!id) { throw new Error('The program does not exist yet.'); }
    return id;
  }
}
