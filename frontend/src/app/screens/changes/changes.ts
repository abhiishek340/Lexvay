import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { phosphorGitDiffDuotone } from '@ng-icons/phosphor-icons/duotone';
import {
  phosphorArrowsClockwise, phosphorBookOpen, phosphorCaretDown, phosphorCheckCircle, phosphorWarning,
} from '@ng-icons/phosphor-icons/regular';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { ProgressSpinner } from 'primeng/progressspinner';
import { Skeleton } from 'primeng/skeleton';
import { Tag } from 'primeng/tag';
import { Timeline } from 'primeng/timeline';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { Enter } from '../../ui/enter';
import { InfoTip } from '../../ui/info-tip';
import { ChangeCard, changeCards } from './change-card';
import { CHANGES_TOKENS } from './changes-theme';

/** The icons the Changes screen shows beyond SHELL_ICONS, provided by its route (changes.routes.ts). */
export const CHANGES_ICONS = {
  phosphorArrowsClockwise, phosphorBookOpen, phosphorCaretDown, phosphorCheckCircle, phosphorGitDiffDuotone, phosphorWarning,
};

const POLL_MS = 1000;
const POLL_LIMIT = 90;

/** Where a re-run stands, in the words its card shows. */
export type RerunPhase = 'Queued' | 'Re-running' | 'Updating';

/**
 * Changes (DESIGN.md "Changes"): every new rule or course version as a card on a vertical timeline, newest first, with
 * the old and new version as two pills, its status, one action (the re-run) and what it affects folded under it.
 */
@Component({
  selector: 'lx-changes',
  imports: [DatePipe, RouterLink, NgIcon, ButtonDirective, ButtonLabel, Message, ProgressSpinner, Skeleton, Tag, Timeline, Enter, InfoTip],
  templateUrl: './changes.html',
  styleUrl: './changes.css',
})
export class Changes {
  private readonly api = inject(ApiService);
  private readonly toast = inject(MessageService);
  readonly session = inject(SessionService);
  readonly context = inject(ProgramContext);
  readonly programId = input.required<string>();

  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  /** The change whose re-run is in progress, and how far it is. */
  readonly running = signal<{ id: string; phase: RerunPhase } | null>(null);
  private readonly expanded = signal<ReadonlySet<string>>(new Set());

  readonly cards = computed(() => changeCards(this.context.changeEvents(), (code) => this.context.jurisdictionName(code), this.context.determinations()));
  readonly openCount = computed(() => this.cards().filter((c) => c.open).length);

  constructor() {
    registerComponentTokens(CHANGES_TOKENS);
    effect(() => {
      const id = this.programId();
      untracked(() => this.context.ensure(id));
    });
  }

  isOpen(id: string): boolean {
    return this.expanded().has(id);
  }

  toggle(id: string): void {
    this.expanded.update((open) => {
      const next = new Set(open);
      if (!next.delete(id)) { next.add(id); }
      return next;
    });
  }

  async rerun(card: ChangeCard): Promise<void> {
    this.running.set({ id: card.id, phase: 'Queued' });
    this.error.set(null);
    this.message.set(`${card.action} for ${card.title} is running.`);
    try {
      const started = await firstValueFrom(this.api.rerunChange(card.id));
      await this.waitForJob(card.id, started.jobId);
      this.running.set({ id: card.id, phase: 'Updating' });
      await this.context.refreshAfterChange();
      this.message.set(`${card.title} handled. Requirements whose evidence changed are back in review.`);
      this.toast.add({ severity: 'success', summary: 'Re-run finished', detail: `${card.title}: changed requirements are back in review.` });
    } catch (err: unknown) {
      this.message.set(null);
      this.error.set(problemDetail(err, 'The re-run could not be completed.'));
    } finally {
      this.running.set(null);
    }
  }

  private async waitForJob(id: string, jobId: string): Promise<void> {
    for (let i = 0; i < POLL_LIMIT; i++) {
      const job = await firstValueFrom(this.api.jobStatus(jobId));
      if (job.status === 'DONE') { return; }
      if (job.status === 'FAILED') { throw new Error(job.error ?? 'The re-run failed.'); }
      this.running.set({ id, phase: job.status === 'RUNNING' ? 'Re-running' : 'Queued' });
      await new Promise((resolve) => setTimeout(resolve, POLL_MS));
    }
    throw new Error('The re-run is taking longer than expected. Reload this page later to see its result.');
  }
}
