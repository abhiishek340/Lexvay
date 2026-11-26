import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { FINDING_GLYPH, FINDING_LABEL, OUTCOME_LABEL } from '../../core/labels';
import { AssessmentView, Determination, Program } from '../../core/models';
import { barSegments, jurisdictionRows, overviewCounts } from '../../core/overview';

@Component({
  selector: 'lx-overview',
  imports: [RouterLink],
  templateUrl: './overview.html',
  styleUrl: './overview.css',
})
export class Overview {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  readonly programId = this.route.snapshot.paramMap.get('programId')!;
  readonly program = signal<Program | null>(null);
  readonly assessment = signal<AssessmentView | null>(null);
  readonly determinations = signal<Determination[]>([]);
  readonly error = signal<string | null>(null);
  readonly glyph = FINDING_GLYPH;
  readonly label = FINDING_LABEL;
  readonly outcomeLabel = OUTCOME_LABEL;
  readonly segments = barSegments;

  readonly counts = computed(() => this.program() ? overviewCounts(this.program()!, this.assessment(), this.determinations()) : null);
  readonly rows = computed(() => this.program() ? jurisdictionRows(this.program()!, this.assessment(), this.determinations()) : []);

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
      if (assessmentId) {
        this.assessment.set(await firstValueFrom(this.api.assessment(assessmentId)));
      }
    } catch {
      this.error.set('The program could not be loaded.');
    }
  }
}
