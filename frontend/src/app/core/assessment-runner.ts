import { Injectable, inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { problemDetail } from './problem';
import { ProgramContext } from './program-context';

const JOB_POLL_MS = 2000;
const JOB_POLL_LIMIT = 150;

/**
 * Starts an assessment of a program and follows its job to a toast; the program's data refreshes when it is done.
 * The command palette's "Run assessment" and the overview's assessment actions share it.
 */
@Injectable({ providedIn: 'root' })
export class AssessmentRunner {
  private readonly api = inject(ApiService);
  private readonly context = inject(ProgramContext);
  private readonly toast = inject(MessageService);

  /** Resolves once the job has finished, failed, or could not be started; it never rejects. */
  async run(programId: string): Promise<void> {
    try {
      const { jobId } = await firstValueFrom(this.api.startAssessment(programId));
      this.toast.add({ severity: 'info', summary: 'Assessment started' });
      for (let i = 0; i < JOB_POLL_LIMIT; i++) {
        await new Promise((resolve) => setTimeout(resolve, JOB_POLL_MS));
        const job = await firstValueFrom(this.api.jobStatus(jobId));
        if (job.status === 'DONE') {
          await this.context.refreshAfterChange();
          this.toast.add({ severity: 'success', summary: 'Assessment finished' });
          return;
        }
        if (job.status === 'FAILED') {
          this.toast.add({ severity: 'error', summary: 'Assessment failed', detail: job.error ?? undefined });
          return;
        }
      }
    } catch (e: unknown) {
      this.toast.add({ severity: 'error', summary: 'Assessment not started', detail: problemDetail(e, 'The assessment could not be started.') });
    }
  }
}
