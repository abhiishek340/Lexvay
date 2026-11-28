import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button, ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AssessmentRunner } from '../../core/assessment-runner';
import { licenseLabel } from '../../core/labels';
import { WorkItem } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { Figure } from '../../ui/figure';
import { Help, HelpTerm } from '../../ui/help';
import { Meter } from '../../ui/meter';
import { Seal } from '../../ui/seal';
import { OVERVIEW_TOKENS } from './overview-theme';
import { needsYou, overviewFigures, primaryAction, stateRows } from './overview-view';

/** The overview's definitions, behind its one Help button. */
const HELP: readonly HelpTerm[] = [
  { term: 'Finding', definition: 'A proposal drawn from the curriculum documents: evidence found, evidence missing, or ambiguous.' },
  { term: 'Decided', definition: 'A reviewer has accepted the finding, requested evidence, or confirmed a gap.' },
  { term: 'Determination', definition: 'Signed for a state by a signer, naming the exact rule and course versions it rests on.' },
];

/**
 * The first screen after sign-in (DESIGN.md v3 "Program overview"), the reference for every other screen's spacing: the
 * program's name and meta line with its one primary action, the three figures, the States table, and what needs the
 * signed-in person. It appears once the program and the work queue are both in, so nothing moves after the first paint.
 */
@Component({
  selector: 'lx-overview',
  imports: [RouterLink, Button, ButtonDirective, ButtonLabel, Message, Skeleton, Figure, Help, Meter, Seal],
  templateUrl: './overview.html',
  styleUrl: './overview.css',
})
export class Overview {
  readonly context = inject(ProgramContext);
  private readonly api = inject(ApiService);
  private readonly session = inject(SessionService);
  private readonly assessments = inject(AssessmentRunner);
  readonly programId = input.required<string>();

  private readonly queue = signal<WorkItem[] | null>(null);
  readonly queueError = signal<string | null>(null);
  readonly running = signal(false);

  readonly license = licenseLabel;
  readonly help = HELP;

  readonly ready = computed(() => this.context.ready() && this.context.program()?.id === this.programId()
    && (this.queue() !== null || this.queueError() !== null));
  readonly rows = computed(() => {
    const program = this.context.program();
    return program
      ? stateRows(program, this.context.assessment(), this.context.determinations(), this.context.ruleVersions(), this.session.isSigner(),
        (code) => this.context.jurisdictionName(code))
      : [];
  });
  readonly figures = computed(() => {
    const program = this.context.program();
    return program ? overviewFigures(program, this.context.assessment(), this.context.determinations()) : null;
  });
  readonly primary = computed(() => primaryAction(this.context.assessment(), this.rows(), this.session.isSigner()));
  readonly needs = computed(() => {
    const program = this.context.program();
    return program ? needsYou(this.queue() ?? [], program, (code) => this.context.jurisdictionName(code)) : [];
  });

  constructor() {
    registerComponentTokens(OVERVIEW_TOKENS);
    effect(() => {
      const id = this.programId();
      untracked(() => {
        this.context.ensure(id);
        this.loadQueue();
      });
    });
  }

  /** "Run assessment" before the first finished one and "Run again" on a failed run: the queue changes when it is done. */
  async runAssessment(): Promise<void> {
    this.running.set(true);
    await this.assessments.run(this.programId());
    this.running.set(false);
    await this.loadQueue();
  }

  private async loadQueue(): Promise<void> {
    try {
      const queue = await firstValueFrom(this.api.workQueue());
      this.queue.set(queue.items);
      this.queueError.set(null);
    } catch (e: unknown) {
      this.queueError.set(problemDetail(e, 'The work queue could not be loaded.'));
    }
  }
}
