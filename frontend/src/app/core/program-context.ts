import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { awaitingAnswer } from './evidence-requests';
import { AssessmentView, ChangeEvent, Determination, EvidenceRequest, Jurisdiction, Program, ProgramOverview, RuleVersionView } from './models';
import { overviewCounts } from './overview';
import { problemDetail } from './problem';
import { versionsWaitingForApproval } from './rules';

/**
 * The program every screen works on, loaded once per program and shared, so moving between screens
 * does not refetch it and the navigation badges (waiting for review, open changes) stay in step with
 * what the screens change. Screens call ensure(); mutations call the matching refresh.
 */
@Injectable({ providedIn: 'root' })
export class ProgramContext {
  private readonly api = inject(ApiService);
  private pending: { programId: string; promise: Promise<void> } | null = null;

  readonly programs = signal<Program[]>([]);
  readonly program = signal<Program | null>(null);
  readonly assessment = signal<AssessmentView | null>(null);
  readonly determinations = signal<Determination[]>([]);
  readonly changeEvents = signal<ChangeEvent[]>([]);
  readonly jurisdictions = signal<Jurisdiction[]>([]);
  readonly ruleVersions = signal<RuleVersionView[]>([]);
  /** Open and answered evidence requests of the latest assessment: the inbox and the rail badge (F4). */
  readonly evidenceRequests = signal<EvidenceRequest[]>([]);
  readonly error = signal<string | null>(null);
  readonly ready = signal(false);

  readonly counts = computed(() => {
    const program = this.program();
    return program ? overviewCounts(program, this.assessment(), this.determinations()) : null;
  });
  readonly openChanges = computed(() => this.changeEvents().filter((e) => e.status === 'OPEN').length);
  readonly rulesWaiting = computed(() => versionsWaitingForApproval(this.ruleVersions()));
  readonly openRequests = computed(() => awaitingAnswer(this.evidenceRequests()));
  private readonly names = computed(() => new Map(this.jurisdictions().map((j) => [j.code, j.name])));

  jurisdictionName(code: string): string {
    return this.names().get(code) ?? code;
  }

  /** Loads the program unless it is already loaded or loading. Failures land in error(). */
  ensure(programId: string): Promise<void> {
    if (this.pending?.programId === programId) {
      return this.pending.promise;
    }
    this.pending = { programId, promise: this.load(programId) };
    return this.pending.promise;
  }

  private async load(programId: string): Promise<void> {
    this.ready.set(false);
    this.error.set(null);
    this.program.set(null);
    this.assessment.set(null);
    try {
      const [programs, overview, changes, jurisdictions, ruleVersions, requests] = await Promise.all([
        firstValueFrom(this.api.programs()),
        firstValueFrom(this.api.programOverview(programId)),
        firstValueFrom(this.api.changeEvents()),
        firstValueFrom(this.api.jurisdictions()),
        firstValueFrom(this.api.ruleVersions()),
        firstValueFrom(this.api.evidenceRequests(programId)),
      ]);
      this.programs.set(programs);
      this.applyOverview(overview);
      this.changeEvents.set(changes);
      this.jurisdictions.set(jurisdictions);
      this.ruleVersions.set(ruleVersions);
      this.evidenceRequests.set(requests);
      this.ready.set(true);
    } catch (e: unknown) {
      this.pending = null;
      this.error.set(problemDetail(e, 'The program could not be loaded.'));
    }
  }

  /** After review decisions: the per-jurisdiction counts on the assessment change. */
  async refreshAssessment(): Promise<void> {
    const current = this.assessment();
    if (current) {
      this.assessment.set(await firstValueFrom(this.api.assessment(current.id)));
    }
  }

  async refreshDeterminations(): Promise<void> {
    const program = this.program();
    if (program) {
      this.determinations.set(await firstValueFrom(this.api.determinations(program.id)));
    }
  }

  /** After a rule version is entered, corrected or approved, and while an extraction runs. */
  async refreshRuleVersions(): Promise<void> {
    this.ruleVersions.set(await firstValueFrom(this.api.ruleVersions()));
  }

  /** After a review decision or an answer: requests open, move to answered, or leave the list. */
  async refreshEvidenceRequests(): Promise<void> {
    const program = this.program();
    if (program) {
      this.evidenceRequests.set(await firstValueFrom(this.api.evidenceRequests(program.id)));
    }
  }

  /** After a re-run: change events, determinations and the latest assessment may all have moved. */
  async refreshAfterChange(): Promise<void> {
    const program = this.program();
    if (!program) {
      return;
    }
    const [changes, overview, requests] = await Promise.all([
      firstValueFrom(this.api.changeEvents()),
      firstValueFrom(this.api.programOverview(program.id)),
      firstValueFrom(this.api.evidenceRequests(program.id)),
    ]);
    this.changeEvents.set(changes);
    this.applyOverview(overview);
    this.evidenceRequests.set(requests);
  }

  /** The program, its latest finished assessment and its determinations, as one consistent read. */
  private applyOverview(overview: ProgramOverview): void {
    this.program.set(overview.program);
    this.assessment.set(overview.assessment);
    this.determinations.set(overview.determinations);
  }

  /** On sign-out, so the next user never sees the previous user's program. */
  clear(): void {
    this.pending = null;
    this.ready.set(false);
    this.programs.set([]);
    this.program.set(null);
    this.assessment.set(null);
    this.determinations.set([]);
    this.changeEvents.set([]);
    this.ruleVersions.set([]);
    this.evidenceRequests.set([]);
  }
}
