import { DOCUMENT, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { phosphorFilePdf } from '@ng-icons/phosphor-icons/regular';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { OUTCOME_LABEL, licenseLabel } from '../../core/labels';
import { Mapping } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { registerComponentTokens } from '../../theme/component-tokens';
import { Enter } from '../../ui/enter';
import { InfoTip } from '../../ui/info-tip';
import { Seal } from '../../ui/seal';
import { StatusIcon } from '../../ui/status-icon';
import { REPORT_TOKENS } from './report-theme';

/** The icons the report shows beyond SHELL_ICONS, provided by its route (report.routes.ts). */
export const REPORT_ICONS = { phosphorFilePdf };

const POLL_MS = 1000;
const POLL_LIMIT = 60;

/**
 * The evidence report (DESIGN.md "Evidence report"): one jurisdiction's requirements as a document sheet, with the Seal
 * when the determination is signed and a Draft stamp when it is not, and a floating pill that downloads the PDF.
 */
@Component({
  selector: 'lx-report',
  imports: [DatePipe, FormsModule, NgIcon, ButtonDirective, ButtonLabel, Message, Select, Skeleton, Enter, InfoTip, Seal, StatusIcon],
  templateUrl: './report.html',
  styleUrl: './report.css',
})
export class Report {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);
  private readonly document = inject(DOCUMENT);
  readonly context = inject(ProgramContext);
  readonly programId = input.required<string>();
  readonly outcomeLabel = OUTCOME_LABEL;
  readonly license = licenseLabel;

  readonly jurisdiction = signal('');
  readonly mappings = signal<Mapping[] | null>(null);
  readonly error = signal<string | null>(null);
  /** The generated PDF of the signed determination on screen, once it is ready. */
  private readonly pdf = signal<{ determinationId: string; file: Blob; filename: string } | null>(null);
  readonly preparing = signal(false);

  readonly options = computed(() => (this.context.program()?.jurisdictions ?? []).map((code) => ({ code, label: `${this.context.jurisdictionName(code)} (${code})` })));
  readonly requirementsTitle = computed(() => {
    const n = this.mappings()?.length;
    return n === undefined ? 'Requirements' : `${n} ${n === 1 ? 'requirement' : 'requirements'}`;
  });
  readonly signed = computed(() => this.context.determinations().find((d) => d.jurisdiction === this.jurisdiction() && d.status === 'SIGNED') ?? null);

  constructor() {
    registerComponentTokens(REPORT_TOKENS);
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
    this.mappings.set(null);
    this.router.navigate([], { relativeTo: this.route, queryParams: { jurisdiction: code }, replaceUrl: true });
    try {
      const view = await firstValueFrom(this.api.mappings(assessment.id, code));
      if (this.jurisdiction() === code) { this.mappings.set(view.mappings); }
    } catch (e: unknown) {
      this.error.set(problemDetail(e, `The report for ${code} could not be loaded.`));
    }
  }

  /** Generates the PDF of the signed determination (a job) the first time, then saves it; later clicks save it again. */
  async downloadPdf(): Promise<void> {
    const d = this.signed();
    if (!d || this.preparing()) { return; }
    const ready = this.pdf();
    if (ready?.determinationId === d.id) {
      this.save(ready.file, ready.filename);
      return;
    }
    this.preparing.set(true);
    this.error.set(null);
    try {
      const requested = await firstValueFrom(this.api.requestReport(d.id));
      for (let i = 0; i < POLL_LIMIT; i++) {
        const answer = await firstValueFrom(this.api.report(requested.reportId));
        if (answer.status === 'READY') {
          this.pdf.set({ determinationId: d.id, file: answer.pdf, filename: answer.filename });
          this.save(answer.pdf, answer.filename);
          this.toast.add({ severity: 'success', summary: 'PDF ready', detail: `Evidence report for ${this.context.jurisdictionName(d.jurisdiction)}.` });
          return;
        }
        if (answer.status === 'FAILED') { throw new Error('The PDF could not be generated.'); }
        await new Promise((resolve) => setTimeout(resolve, POLL_MS));
      }
      throw new Error('The PDF is taking longer than expected. Try again in a minute.');
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The PDF could not be generated.'));
    } finally {
      this.preparing.set(false);
    }
  }

  /** Hands the file to the browser's downloads under the server's name. */
  private save(file: Blob, filename: string): void {
    const url = URL.createObjectURL(file);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = filename;
    this.document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
