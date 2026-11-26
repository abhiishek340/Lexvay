import { SlicePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { DECISION_LABEL, FINDING_GLYPH, FINDING_LABEL, OUTCOME_LABEL } from '../../core/labels';
import { AssessmentView, Determination, Mapping, Program } from '../../core/models';

@Component({
  selector: 'lx-report',
  imports: [SlicePipe],
  templateUrl: './report.html',
  styleUrl: './report.css',
})
export class Report {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  readonly programId = this.route.snapshot.paramMap.get('programId')!;
  readonly program = signal<Program | null>(null);
  readonly assessment = signal<AssessmentView | null>(null);
  readonly determinations = signal<Determination[]>([]);
  readonly jurisdiction = signal('');
  readonly mappings = signal<Mapping[]>([]);
  readonly error = signal<string | null>(null);
  readonly glyph = FINDING_GLYPH;
  readonly findingLabel = FINDING_LABEL;
  readonly decisionLabel = DECISION_LABEL;
  readonly outcomeLabel = OUTCOME_LABEL;

  readonly signed = computed(() => this.determinations().find((d) => d.jurisdiction === this.jurisdiction() && d.status === 'SIGNED') ?? null);
  readonly reportUrl = signal<string | null>(null);
  readonly generating = signal(false);

  async generatePdf(): Promise<void> {
    const d = this.signed();
    if (!d) { return; }
    this.generating.set(true);
    this.error.set(null);
    this.reportUrl.set(null);
    try {
      const requested = await firstValueFrom(this.api.requestReport(d.id));
      for (let i = 0; i < 60; i++) {
        const status = await firstValueFrom(this.api.reportStatus(requested.reportId));
        if (status.status === 'READY') { this.reportUrl.set(this.api.reportUrl(requested.reportId)); break; }
        if (status.status === 'FAILED') { throw new Error('Report generation failed.'); }
        await new Promise((r) => setTimeout(r, 1000));
      }
    } catch {
      this.error.set('The PDF could not be generated.');
    } finally {
      this.generating.set(false);
    }
  }

  constructor() {
    this.load();
  }

  private async load(): Promise<void> {
    try {
      const [program, determinations, audit] = await Promise.all([
        firstValueFrom(this.api.program(this.programId)),
        firstValueFrom(this.api.determinations(this.programId)),
        firstValueFrom(this.api.audit(500)),
      ]);
      this.program.set(program);
      this.determinations.set(determinations);
      const assessmentId = this.api.latestAssessmentId(audit, this.programId);
      if (!assessmentId) {
        this.error.set('No finished assessment exists for this program yet.');
        return;
      }
      this.assessment.set(await firstValueFrom(this.api.assessment(assessmentId)));
      await this.selectJurisdiction(program.jurisdictions[0]);
    } catch {
      this.error.set('The report could not be loaded.');
    }
  }

  async selectJurisdiction(code: string): Promise<void> {
    this.jurisdiction.set(code);
    this.reportUrl.set(null);
    const view = await firstValueFrom(this.api.mappings(this.assessment()!.id, code));
    this.mappings.set(view.mappings);
  }
}
