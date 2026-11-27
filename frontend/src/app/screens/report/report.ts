import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { OUTCOME_LABEL } from '../../core/labels';
import { Mapping } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { DecisionState, FindingLabel } from '../../ui/finding';

const POLL_MS = 1000;
const POLL_LIMIT = 60;

@Component({
  selector: 'lx-report',
  imports: [DatePipe, FormsModule, NgIcon, ButtonDirective, ButtonLabel, Message, Select, Skeleton, TableModule, FindingLabel, DecisionState],
  templateUrl: './report.html',
  styleUrl: './report.css',
})
export class Report {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);
  readonly context = inject(ProgramContext);
  readonly programId = input.required<string>();
  readonly outcomeLabel = OUTCOME_LABEL;

  readonly jurisdiction = signal('');
  readonly mappings = signal<Mapping[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly reportUrl = signal<string | null>(null);
  readonly generating = signal(false);

  readonly options = computed(() => (this.context.program()?.jurisdictions ?? []).map((code) => ({ code, label: `${this.context.jurisdictionName(code)} (${code})` })));
  readonly signed = computed(() => this.context.determinations().find((d) => d.jurisdiction === this.jurisdiction() && d.status === 'SIGNED') ?? null);

  constructor() {
    effect(() => {
      const id = this.programId();
      untracked(() => this.context.ensure(id));
    });
    effect(() => {
      const program = this.context.program();
      if (!program || !this.context.ready() || untracked(this.jurisdiction)) { return; }
      if (!this.context.assessment()) {
        untracked(() => this.error.set('No finished assessment exists for this program yet. The report is built from one.'));
        return;
      }
      const wanted = this.route.snapshot.queryParamMap.get('jurisdiction');
      untracked(() => this.selectJurisdiction(wanted && program.jurisdictions.includes(wanted) ? wanted : program.jurisdictions[0]));
    });
  }

  async selectJurisdiction(code: string): Promise<void> {
    const assessment = this.context.assessment();
    if (!assessment || !code) { return; }
    this.jurisdiction.set(code);
    this.reportUrl.set(null);
    this.mappings.set(null);
    this.router.navigate([], { relativeTo: this.route, queryParams: { jurisdiction: code }, replaceUrl: true });
    try {
      const view = await firstValueFrom(this.api.mappings(assessment.id, code));
      if (this.jurisdiction() === code) { this.mappings.set(view.mappings); }
    } catch (e: unknown) {
      this.error.set(problemDetail(e, `The report for ${code} could not be loaded.`));
    }
  }

  async generatePdf(): Promise<void> {
    const d = this.signed();
    if (!d || this.generating()) { return; }
    this.generating.set(true);
    this.error.set(null);
    this.reportUrl.set(null);
    try {
      const requested = await firstValueFrom(this.api.requestReport(d.id));
      for (let i = 0; i < POLL_LIMIT; i++) {
        const status = await firstValueFrom(this.api.reportStatus(requested.reportId));
        if (status.status === 'READY') {
          this.reportUrl.set(this.api.reportUrl(requested.reportId));
          this.toast.add({ severity: 'success', summary: 'PDF ready', detail: `The evidence report for ${d.jurisdiction} can be downloaded.` });
          return;
        }
        if (status.status === 'FAILED') { throw new Error('The PDF could not be generated.'); }
        await new Promise((resolve) => setTimeout(resolve, POLL_MS));
      }
      throw new Error('The PDF is taking longer than expected. Try again in a minute.');
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The PDF could not be generated.'));
    } finally {
      this.generating.set(false);
    }
  }
}
