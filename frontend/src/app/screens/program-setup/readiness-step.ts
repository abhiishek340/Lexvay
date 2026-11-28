import { Component, computed, effect, inject, output, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { problemDetail } from '../../core/problem';
import { Meter } from '../../ui/meter';
import { CheckId, SetupStep, checkState } from './setup-model';
import { SetupNotice, SetupStore } from './setup-store';

/** Where a failing line sends the person to fix it, and the words of that action. */
const FIX: Partial<Record<CheckId, { step: SetupStep; label: string }>> = {
  COURSE_DOCUMENTS: { step: 'DOCUMENTS', label: 'Open Documents' },
  READING: { step: 'DOCUMENTS', label: 'Open Documents' },
  FACTS: { step: 'FACTS', label: 'Open Program facts' },
};

/**
 * The Readiness step: the checklist (a neutral check or cross and words on every line, never colour alone), Run
 * assessment while nothing blocks it, and the run followed to the end: a Meter of requirements processed out of those
 * the run will assess, then a toast and the way to Review.
 */
@Component({
  selector: 'lx-setup-readiness',
  imports: [RouterLink, NgIcon, ButtonDirective, ButtonLabel, Message, Meter],
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
  readonly fix = FIX;
  readonly state = checkState;

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
