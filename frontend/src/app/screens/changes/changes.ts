import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { phosphorArrowsClockwise, phosphorBookOpen } from '@ng-icons/phosphor-icons/regular';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { ProgressSpinner } from 'primeng/progressspinner';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ChangeEvent } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { CHANGE_KIND } from './change-kind';
import { rerunBlocker } from './change-rules';
import { CHANGES_TOKENS } from './changes-theme';

/** The icons the Changes screen shows beyond SHELL_ICONS, provided by its route (changes.routes.ts). */
export const CHANGES_ICONS = { phosphorArrowsClockwise, phosphorBookOpen };

const POLL_MS = 1000;
const POLL_LIMIT = 90;

@Component({
  selector: 'lx-changes',
  imports: [DatePipe, NgIcon, ButtonDirective, ButtonLabel, Message, ProgressSpinner, Skeleton, TableModule, Tag],
  templateUrl: './changes.html',
  styleUrl: './changes.css',
})
export class Changes {
  private readonly api = inject(ApiService);
  private readonly toast = inject(MessageService);
  readonly session = inject(SessionService);
  readonly context = inject(ProgramContext);
  readonly programId = input.required<string>();
  readonly blocker = rerunBlocker;

  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  /** The change event whose re-run is in progress. */
  readonly running = signal<string | null>(null);

  constructor() {
    registerComponentTokens(CHANGES_TOKENS);
    effect(() => {
      const id = this.programId();
      untracked(() => this.context.ensure(id));
    });
  }

  async rerun(e: ChangeEvent): Promise<void> {
    this.running.set(e.id);
    this.error.set(null);
    this.message.set(`${CHANGE_KIND[e.kind].action} for ${subject(e)} is running.`);
    try {
      const started = await firstValueFrom(this.api.rerunChange(e.id));
      await this.waitForJob(started.jobId);
      await this.context.refreshAfterChange();
      const text = `${CHANGE_KIND[e.kind].label} for ${subject(e)} handled. Requirements whose evidence changed are back in review.`;
      this.message.set(text);
      this.toast.add({ severity: 'success', summary: 'Re-run finished', detail: text });
    } catch (err: unknown) {
      this.message.set(null);
      this.error.set(problemDetail(err, 'The re-run could not be completed.'));
    } finally {
      this.running.set(null);
    }
  }

  kind(e: ChangeEvent): (typeof CHANGE_KIND)[ChangeEvent['kind']] {
    return CHANGE_KIND[e.kind];
  }

  what(e: ChangeEvent): { old: string; next: string } {
    return e.kind === 'RULE_VERSION'
      ? { old: e.oldLabel ?? 'previous rule version', next: e.newLabel ?? 'new rule version' }
      : { old: `catalog year ${e.oldCatalogYear ?? 'unknown'}`, next: `catalog year ${e.newCatalogYear ?? 'unknown'}` };
  }

  subject(e: ChangeEvent): string {
    return subject(e);
  }

  private async waitForJob(jobId: string): Promise<void> {
    for (let i = 0; i < POLL_LIMIT; i++) {
      const job = await firstValueFrom(this.api.jobStatus(jobId));
      if (job.status === 'DONE') { return; }
      if (job.status === 'FAILED') { throw new Error(job.error ?? 'The re-run failed.'); }
      await new Promise((resolve) => setTimeout(resolve, POLL_MS));
    }
    throw new Error('The re-run is taking longer than expected. Reload this page later to see its result.');
  }
}

function subject(e: ChangeEvent): string {
  return (e.kind === 'RULE_VERSION' ? e.jurisdiction : e.courseCode) ?? 'this change';
}
