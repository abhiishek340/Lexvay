import { DOCUMENT, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
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
import { Help, HelpTerm } from '../../ui/help';
import { Seal } from '../../ui/seal';
import { requirementStatus } from '../../ui/status';
import { StatusMark } from '../../ui/status-mark';
import { ReportCheckDialog } from './report-check-dialog';
import { REPORT_TOKENS } from './report-theme';

const HELP: readonly HelpTerm[] = [
  { term: 'Evidence report', definition: 'The proposed findings and the review decisions recorded so far, with the quoted evidence.' },
  { term: 'Draft', definition: 'It becomes a determination only when a signer signs it in Review; the PDF is available after that.' },
];

const POLL_MS = 1000;
const POLL_LIMIT = 60;

/**
 * The evidence report (DESIGN.md "Evidence report"): one jurisdiction's requirements as a document sheet, with the Seal
 * when the determination is signed and the word Draft when it is not; the PDF download and "Check a file" (F14, deferred
 * until it is first asked for) sit in the title row.
 */
@Component({
  selector: 'lx-report',
  imports: [DatePipe, FormsModule, ButtonDirective, ButtonLabel, Message, Select, Skeleton, Help, Seal, StatusMark, ReportCheckDialog],
  templateUrl: './report.html',
  styleUrl: './report.css',
})
export class Report {
  readonly help = HELP;
  readonly status = requirementStatus;
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);
  private readonly document = inject(DOCUMENT);
  readonly context = inject(ProgramContext);
  readonly programId = input.required<string>();
  /** The `jurisdiction` query parameter (router input binding): a link to another state's report changes it in place. */
  readonly wanted = input<string | undefined>(undefined, { alias: 'jurisdiction' });
  readonly outcomeLabel = OUTCOME_LABEL;
  readonly license = licenseLabel;

  readonly jurisdiction = signal('');
  readonly mappings = signal<Mapping[] | null>(null);
  readonly error = signal<string | null>(null);
  /** The generated PDF of the signed determination on screen, once it is ready. */
  private readonly pdf = signal<{ determinationId: string; file: Blob; filename: string } | null>(null);
  readonly preparing = signal(false);
  readonly checkOpen = signal(false);
  /** The assessment and jurisdiction whose requirements are on screen or loading, so each pair is read once. */
  private shown = '';

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
    // The program's report for the jurisdiction the URL names (its first one otherwise), read again when either changes:
    // the report check links to any program's report from this screen.
    effect(() => {
      const program = this.context.program();
      const wanted = this.wanted();
      if (!program || !this.context.ready()) { return; }
      if (!this.context.assessment()) {
        untracked(() => this.error.set('No finished assessment exists for this program yet. The report is built from one.'));
        return;
      }
      untracked(() => this.selectJurisdiction(wanted && program.jurisdictions.includes(wanted) ? wanted : program.jurisdictions[0]));
    });
  }

  async selectJurisdiction(code: string): Promise<void> {
    const assessment = this.context.assessment();
    const key = `${assessment?.id}/${code}`;
    if (!assessment || !code || this.shown === key) { return; }
    this.shown = key;
    this.jurisdiction.set(code);
    this.mappings.set(null);
    this.router.navigate([], { relativeTo: this.route, queryParams: { jurisdiction: code }, replaceUrl: true });
    try {
      const view = await firstValueFrom(this.api.mappings(assessment.id, code));
      if (this.shown === key) { this.mappings.set(view.mappings); }
    } catch (e: unknown) {
      if (this.shown === key) { this.shown = ''; }
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
