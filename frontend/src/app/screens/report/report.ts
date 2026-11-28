import { DOCUMENT, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { OUTCOME_LABEL, licenseLabel } from '../../core/labels';
import { Mapping } from '../../core/models';
import { needsJurisdictionParam } from '../../core/navigation';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { registerComponentTokens } from '../../theme/component-tokens';
import { Help, HelpTerm } from '../../ui/help';
import { Seal } from '../../ui/seal';
import { SkeletonRows } from '../../ui/skeleton-rows';
import { StateTab, StateTabs } from '../../ui/state-tabs';
import { requirementStatus } from '../../ui/status';
import { StatusMark } from '../../ui/status-mark';
import { ReportCheckDialog } from './report-check-dialog';
import { downloadsNote } from './report-downloads';
import { REPORT_TOKENS } from './report-theme';

const HELP: readonly HelpTerm[] = [
  { term: 'Evidence report', definition: 'The proposed findings and the review decisions recorded so far, with the quoted evidence.' },
  { term: 'Draft', definition: 'It becomes a determination only when a signer signs it in Review; the PDF is available after that.' },
  { term: 'Evidence package', definition: 'One ZIP of the signed report, its records, the quoted documents and the audit entries, with a manifest of hashes.' },
  { term: 'Check a file', definition: 'Tells whether a PDF is exactly a report of this workspace, by its SHA-256. The file is not stored.' },
];

const POLL_MS = 1000;
/** A report is one page set; a package also copies the cited documents, so it may take longer. */
const REPORT_POLL_LIMIT = 60;
const PACKAGE_POLL_LIMIT = 120;

const wait = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, POLL_MS));

/**
 * The evidence report (DESIGN.md "Evidence report"): one state's requirements as a document sheet, the states as an
 * underlined tab row above it, the Seal when the determination is signed and the word Draft when it is not. The title
 * row holds the downloads (the PDF, then the evidence package built from it, F15) and "Check a file" (F14, deferred
 * until it is first asked for); when a download is not available, one meta line says why.
 */
@Component({
  selector: 'lx-report',
  imports: [DatePipe, ButtonDirective, ButtonLabel, Message, TabPanel, TabPanels, Tabs, Help, Seal, SkeletonRows, StateTabs, StatusMark, ReportCheckDialog],
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
  /** The generated PDF of a signed determination, once it is ready: the package needs it. */
  private readonly pdf = signal<{ determinationId: string; file: Blob; filename: string } | null>(null);
  /** The package built for a signed determination, once it is ready; a second click downloads the same one again. */
  private readonly built = signal<{ determinationId: string; packageId: string } | null>(null);
  readonly preparingPdf = signal(false);
  readonly preparingPackage = signal(false);
  readonly checkOpen = signal(false);
  /** The assessment and jurisdiction whose requirements are on screen or loading, so each pair is read once. */
  private shown = '';

  readonly codes = computed(() => this.context.program()?.jurisdictions ?? []);
  readonly tabs = computed<StateTab[]>(() => this.codes().map((code) => ({ code, name: this.context.jurisdictionName(code), progress: null })));
  readonly requirementsTitle = computed(() => {
    const n = this.mappings()?.length;
    return n === undefined ? 'Requirements' : `${n} ${n === 1 ? 'requirement' : 'requirements'}`;
  });
  readonly signed = computed(() => this.context.determinations().find((d) => d.jurisdiction === this.jurisdiction() && d.status === 'SIGNED') ?? null);
  private readonly reopened = computed(() => this.context.determinations().some((d) => d.jurisdiction === this.jurisdiction() && d.status === 'REOPENED'));
  /** A READY report exists: stored before this visit (the read model's `latestReadyReportId`) or generated in it. */
  readonly reportReady = computed(() => {
    const d = this.signed();
    return d !== null && (d.latestReadyReportId !== null || this.pdf()?.determinationId === d.id);
  });
  readonly note = computed(() => downloadsNote(this.signed() ? 'SIGNED' : this.reopened() ? 'REOPENED' : null, this.reportReady()));

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

  async selectJurisdiction(value: string | number | undefined): Promise<void> {
    const code = String(value ?? '');
    const assessment = this.context.assessment();
    const key = `${assessment?.id}/${code}`;
    if (!assessment || !code || this.shown === key) { return; }
    this.shown = key;
    this.jurisdiction.set(code);
    this.mappings.set(null);
    this.error.set(null);
    if (needsJurisdictionParam(this.route.snapshot.queryParamMap.get('jurisdiction'), code, this.context.program()?.jurisdictions[0])) {
      this.router.navigate([], { relativeTo: this.route, queryParams: { jurisdiction: code }, replaceUrl: true });
    }
    try {
      const view = await firstValueFrom(this.api.mappings(assessment.id, code));
      if (this.shown === key) { this.mappings.set(view.mappings); }
    } catch (e: unknown) {
      if (this.shown === key) { this.shown = ''; }
      this.error.set(problemDetail(e, `The report for ${code} could not be loaded.`));
    }
  }

  /**
   * Saves the PDF of the signed determination: the stored one when a report is ready (D-515), otherwise a new one the
   * server generates (a job) first. Later clicks save the same file again.
   */
  async downloadPdf(): Promise<void> {
    const d = this.signed();
    if (!d || this.preparingPdf()) { return; }
    const ready = this.pdf();
    if (ready?.determinationId === d.id) {
      this.saveBlob(ready.file, ready.filename);
      return;
    }
    this.preparingPdf.set(true);
    this.error.set(null);
    try {
      const reportId = d.latestReadyReportId ?? (await firstValueFrom(this.api.requestReport(d.id))).reportId;
      for (let i = 0; i < REPORT_POLL_LIMIT; i++) {
        const answer = await firstValueFrom(this.api.report(reportId));
        if (answer.status === 'READY') {
          this.pdf.set({ determinationId: d.id, file: answer.pdf, filename: answer.filename });
          this.saveBlob(answer.pdf, answer.filename);
          this.toast.add({ severity: 'success', summary: 'PDF ready', detail: `Evidence report for ${this.context.jurisdictionName(d.jurisdiction)}.` });
          // A newly generated report is now the determination's latest ready one: read it back, so every later visit
          // in this session offers the stored PDF and the package at once.
          if (d.latestReadyReportId === null) { await this.context.refreshDeterminations(); }
          return;
        }
        if (answer.status === 'FAILED') { throw new Error('The PDF could not be generated.'); }
        await wait();
      }
      throw new Error('The PDF is taking longer than expected. Try again in a minute.');
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The PDF could not be generated.'));
    } finally {
      this.preparingPdf.set(false);
    }
  }

  /**
   * Asks the server to build the evidence package of the signed determination (a job), waits for it, then hands its ZIP
   * to the browser's downloads; later clicks download the same package again.
   */
  async downloadPackage(): Promise<void> {
    const d = this.signed();
    if (!d || !this.reportReady() || this.preparingPackage()) { return; }
    const ready = this.built();
    if (ready?.determinationId === d.id) {
      this.saveUrl(this.api.packageFileUrl(ready.packageId));
      return;
    }
    this.preparingPackage.set(true);
    this.error.set(null);
    try {
      const { packageId } = await firstValueFrom(this.api.requestPackage(d.id));
      for (let i = 0; i < PACKAGE_POLL_LIMIT; i++) {
        const view = await firstValueFrom(this.api.evidencePackage(packageId));
        if (view.status === 'READY') {
          this.built.set({ determinationId: d.id, packageId });
          this.saveUrl(this.api.packageFileUrl(packageId));
          this.toast.add({ severity: 'success', summary: 'Package ready', detail: `Evidence package for ${this.context.jurisdictionName(d.jurisdiction)}.` });
          return;
        }
        if (view.status === 'FAILED') { throw new Error(view.error ?? 'The package could not be built.'); }
        await wait();
      }
      throw new Error('The package is taking longer than expected. Try again in a minute.');
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The package could not be built.'));
    } finally {
      this.preparingPackage.set(false);
    }
  }

  /** Hands a file already in memory to the browser's downloads under the server's name. */
  private saveBlob(file: Blob, filename: string): void {
    const url = URL.createObjectURL(file);
    this.follow(url, filename);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  /** Lets the browser download a file straight from the server, under the name its Content-Disposition gives. */
  private saveUrl(url: string): void {
    this.follow(url, '');
  }

  private follow(href: string, filename: string): void {
    const link = this.document.createElement('a');
    link.href = href;
    link.download = filename;
    this.document.body.append(link);
    link.click();
    link.remove();
  }
}
