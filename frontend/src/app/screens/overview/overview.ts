import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import {
  phosphorArrowCounterClockwise, phosphorCheckCircle, phosphorCircleDashed, phosphorMinusCircle, phosphorSignature, phosphorWarning,
  phosphorWarningCircle, phosphorXCircle,
} from '@ng-icons/phosphor-icons/regular';
import { phosphorMapTrifoldDuotone } from '@ng-icons/phosphor-icons/duotone';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { Tag } from 'primeng/tag';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AssessmentRunner } from '../../core/assessment-runner';
import { licenseLabel } from '../../core/labels';
import { WorkItem } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { Enter } from '../../ui/enter';
import { InfoTip } from '../../ui/info-tip';
import { ProgressRing } from '../../ui/progress-ring';
import { Stat } from '../../ui/stat';
import { StateMap } from '../../ui/state-map/state-map';
import { OVERVIEW_TOKENS } from './overview-theme';
import { CardTone, StateCard, heroStats, mapStates, needsYou, stateCards } from './overview-view';

/** The icons the Overview shows beyond SHELL_ICONS (state cards, "Needs you", the empty hero), provided by its route. */
export const OVERVIEW_ICONS = {
  phosphorArrowCounterClockwise, phosphorCheckCircle, phosphorCircleDashed, phosphorMinusCircle, phosphorSignature, phosphorWarning,
  phosphorWarningCircle, phosphorXCircle, phosphorMapTrifoldDuotone,
};

const TONE_SEVERITY: Record<CardTone, 'success' | 'danger' | 'info' | 'secondary'> = {
  found: 'success', missing: 'danger', accent: 'info', seal: 'secondary', muted: 'secondary',
};

/**
 * The first screen after sign-in (DESIGN.md "Program overview"): the program's name, its target states on the map
 * with the three numbers that matter, what needs the signed-in person, and one card per state with its next step.
 * It appears once the program and the work queue are both in, so nothing moves after the first paint.
 */
@Component({
  selector: 'lx-overview',
  imports: [RouterLink, NgIcon, ButtonDirective, ButtonLabel, Message, Skeleton, Tag, Enter, InfoTip, ProgressRing, Stat, StateMap],
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
  /** The state whose card action runs: its card head carries the shared-element name into Review's state header. */
  readonly departing = signal<string | null>(null);

  readonly license = licenseLabel;
  readonly severity = TONE_SEVERITY;

  readonly ready = computed(() => this.context.ready() && this.context.program()?.id === this.programId()
    && (this.queue() !== null || this.queueError() !== null));
  readonly cards = computed(() => {
    const program = this.context.program();
    return program
      ? stateCards(program, this.context.assessment(), this.context.determinations(), this.session.isSigner(), (code) => this.context.jurisdictionName(code))
      : [];
  });
  readonly mapStates = computed(() => mapStates(this.cards()));
  readonly stats = computed(() => {
    const program = this.context.program();
    return program ? heroStats(program, this.context.assessment(), this.context.determinations()) : null;
  });
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

  depart(card: StateCard): void {
    this.departing.set(card.code);
  }

  /** "Run assessment" on an empty overview and "Run again" on a failed run: the queue changes when it is done. */
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
