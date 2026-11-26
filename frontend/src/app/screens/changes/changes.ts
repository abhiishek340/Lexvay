import { SlicePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ChangeEvent } from '../../core/models';
import { SessionService } from '../../core/session.service';

@Component({
  selector: 'lx-changes',
  imports: [SlicePipe],
  templateUrl: './changes.html',
})
export class Changes {
  private readonly api = inject(ApiService);
  readonly session = inject(SessionService);
  readonly events = signal<ChangeEvent[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly busy = signal<string | null>(null);

  constructor() {
    this.load();
  }

  async load(): Promise<void> {
    try {
      this.events.set(await firstValueFrom(this.api.changeEvents()));
    } catch {
      this.error.set('Change events could not be loaded.');
    }
  }

  kindLabel(e: ChangeEvent): string {
    return e.kind === 'RULE_VERSION' ? 'New rule version' : 'New course version';
  }

  what(e: ChangeEvent): string {
    if (e.kind === 'RULE_VERSION') {
      return `${e.jurisdiction ?? ''}: "${e.oldLabel ?? ''}" is superseded by "${e.newLabel ?? ''}"`;
    }
    return `${e.courseCode ?? ''}: catalog year ${e.oldCatalogYear ?? ''} is superseded by ${e.newCatalogYear ?? ''}`;
  }

  blocker(e: ChangeEvent): string | null {
    if (e.status !== 'OPEN') { return null; }
    if (e.kind === 'RULE_VERSION' && !e.newApproved) { return 'Approve the new requirement list first.'; }
    if (e.kind === 'COURSE_VERSION' && !e.newVersionHasDocument) { return 'Upload the new syllabus first.'; }
    return null;
  }

  actionLabel(e: ChangeEvent): string {
    return e.kind === 'RULE_VERSION' ? 'Re-run assessment' : 'Re-run matching';
  }

  async rerun(e: ChangeEvent): Promise<void> {
    this.busy.set(e.id);
    this.error.set(null);
    this.message.set(null);
    try {
      const result = await firstValueFrom(this.api.rerunChange(e.id));
      for (let i = 0; i < 60; i++) {
        const job = await firstValueFrom(this.api.jobStatus(result.jobId));
        if (job.status === 'DONE') { break; }
        if (job.status === 'FAILED') { throw new Error(job.error ?? 'The re-run failed.'); }
        await new Promise((r) => setTimeout(r, 1000));
      }
      await this.load();
      this.message.set(`${this.kindLabel(e)} handled. Affected requirements are back in review where the evidence changed.`);
    } catch (err: unknown) {
      const e2 = err as { error?: { detail?: string }; message?: string };
      this.error.set(e2?.error?.detail ?? e2?.message ?? 'The re-run could not be started.');
    } finally {
      this.busy.set(null);
    }
  }
}
