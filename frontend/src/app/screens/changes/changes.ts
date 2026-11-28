import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Tooltip } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { groupByDay } from '../../ui/days';
import { Help, HelpTerm } from '../../ui/help';
import { SkeletonRows } from '../../ui/skeleton-rows';
import { ChangeRow, changeRows } from './change-row';
import { CHANGES_TOKENS } from './changes-theme';

const HELP: readonly HelpTerm[] = [
  { term: 'Change', definition: 'A new rule version or course version. Findings that cite the old version become outdated.' },
  { term: 'Reopened', definition: 'A signed determination that rests on the old version needs a new signature.' },
  { term: 'Re-run', definition: 'Keeps what still holds and sends the rest back to review.' },
];

const POLL_MS = 1000;
const POLL_LIMIT = 90;

/** Where a re-run stands, in the words its row shows. */
export type RerunPhase = 'Queued' | 'Re-running' | 'Updating';

/**
 * Changes (DESIGN.md "Changes"): every new rule or course version as one sentence in a log grouped by day, newest
 * first, from what to what; where it stands or its one action (the re-run) before the time; what it reopened and the
 * full version names open below the row.
 */
@Component({
  selector: 'lx-changes',
  imports: [DatePipe, RouterLink, ButtonDirective, ButtonLabel, Message, Tooltip, Help, SkeletonRows],
  templateUrl: './changes.html',
  styleUrl: './changes.css',
})
export class Changes {
  readonly help = HELP;
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

  readonly rows = computed(() => changeRows(this.context.changeEvents(), (code) => this.context.jurisdictionName(code), this.context.determinations()));
  readonly days = computed(() => groupByDay(this.rows(), (row) => row.createdAt, new Date()));
  readonly openCount = computed(() => this.rows().filter((r) => r.open).length);

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

  async rerun(row: ChangeRow): Promise<void> {
    this.running.set({ id: row.id, phase: 'Queued' });
    this.error.set(null);
    this.message.set(`${row.action} for ${row.name} is running.`);
    try {
      const started = await firstValueFrom(this.api.rerunChange(row.id));
      await this.waitForJob(row.id, started.jobId);
      this.running.set({ id: row.id, phase: 'Updating' });
      await this.context.refreshAfterChange();
      this.message.set(`${row.name} handled. Requirements whose evidence changed are back in review.`);
      this.toast.add({ severity: 'success', summary: 'Re-run finished', detail: `${row.name}: changed requirements are back in review.` });
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
