import { Component, computed, effect, inject, output, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { problemDetail } from '../../core/problem';
import type { IconName } from '../../theme/icons';
import { Enter } from '../../ui/enter';
import { ProgressRing } from '../../ui/progress-ring';
import { CheckId, ReadinessCheck, SetupStep } from './setup-model';
import { SetupNotice, SetupStore } from './setup-store';

/** Where a failing line sends the person to fix it. */
const FIX_STEP: Partial<Record<CheckId, SetupStep>> = { COURSE_DOCUMENTS: 'DOCUMENTS', READING: 'DOCUMENTS', FACTS: 'FACTS' };

/**
 * The Readiness step: the checklist (icon and words on every line, never colour alone), Run assessment while nothing
 * blocks it, and the run followed to the end: a ring of requirements processed out of those the run will assess,
 * then a toast and the way to Review.
 */
@Component({
  selector: 'lx-setup-readiness',
  imports: [RouterLink, NgIcon, ButtonDirective, ButtonLabel, Message, Enter, ProgressRing],
  templateUrl: './readiness-step.html',
  styleUrl: './steps.css',
})
export class ReadinessStep {
  readonly store = inject(SetupStore);
  readonly announce = output<SetupNotice>();
  readonly goTo = output<SetupStep>();

  readonly checks = computed(() => this.store.readiness()?.checks ?? []);
  readonly blocked = computed(() => !(this.store.readiness()?.ready ?? false));
  readonly running = computed(() => {
    const status = this.store.run()?.view.status;
    return status === 'QUEUED' || status === 'RUNNING';
  });
  readonly starting = signal(false);
  readonly error = signal<string | null>(null);
  readonly fixStep = FIX_STEP;

  constructor() {
    // The run's end is said once, by the workspace: a toast and the hidden live region.
    let announced: string | null = null;
    effect(() => {
      const run = this.store.run();
      if (!run || announced === run.view.id || (run.view.status !== 'FINISHED' && run.view.status !== 'FAILED')) { return; }
      announced = run.view.id;
      untracked(() => {
        const finished = run.view.status === 'FINISHED';
        const detail = finished ? `${run.processed} requirements have proposed findings, ready for review.` : 'The assessment stopped before it finished. Run it again.';
        this.announce.emit({ severity: finished ? 'success' : 'error', summary: finished ? 'Assessment finished' : 'Assessment failed', detail });
      });
    });
  }

  icon(check: ReadinessCheck): IconName {
    if (check.ok) { return 'phosphorCheckCircleFill'; }
    return check.blocking ? 'phosphorXCircleFill' : 'phosphorWarningCircleFill';
  }

  tone(check: ReadinessCheck): 'found' | 'missing' | 'ambiguous' {
    if (check.ok) { return 'found'; }
    return check.blocking ? 'missing' : 'ambiguous';
  }

  /** The words that say what the icon shows, for a screen reader. */
  verdict(check: ReadinessCheck): string {
    if (check.ok) { return 'Done'; }
    return check.blocking ? 'Blocks the run' : 'Warning';
  }

  async run(): Promise<void> {
    if (this.blocked() || this.running() || this.starting()) { return; }
    this.starting.set(true);
    this.error.set(null);
    try {
      await this.store.startAssessment();
      this.announce.emit({ summary: 'Assessment started', detail: 'The model proposes findings; nobody is bound by them.' });
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The assessment could not be started.'));
    } finally {
      this.starting.set(false);
    }
  }
}
