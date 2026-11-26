import { Dialog } from '@angular/cdk/dialog';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { DECISION_LABEL, FINDING_GLYPH, FINDING_LABEL, OUTCOME_LABEL } from '../../core/labels';
import { AssessmentView, DecisionKind, Determination, Mapping, Outcome, Program, RuleVersionView } from '../../core/models';
import { SessionService } from '../../core/session.service';
import { SignDialog, SignDialogData } from './sign-dialog';

@Component({
  selector: 'lx-review',
  imports: [FormsModule],
  templateUrl: './review.html',
  styleUrl: './review.css',
})
export class Review {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(Dialog);
  readonly session = inject(SessionService);

  readonly programId = this.route.snapshot.paramMap.get('programId')!;
  readonly program = signal<Program | null>(null);
  readonly assessment = signal<AssessmentView | null>(null);
  readonly determinations = signal<Determination[]>([]);
  readonly ruleVersions = signal<RuleVersionView[]>([]);
  readonly jurisdiction = signal<string>('');
  readonly mappings = signal<Mapping[]>([]);
  readonly selectedId = signal<string | null>(null);
  readonly note = signal('');
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);

  readonly glyph = FINDING_GLYPH;
  readonly findingLabel = FINDING_LABEL;
  readonly decisionLabel = DECISION_LABEL;
  readonly outcomeLabel = OUTCOME_LABEL;

  readonly selected = computed(() => this.mappings().find((m) => m.id === this.selectedId()) ?? null);
  readonly decidedCount = computed(() => this.mappings().filter((m) => m.decision && !m.stale).length);
  readonly allDecided = computed(() => this.mappings().length > 0 && this.decidedCount() === this.mappings().length);
  readonly signed = computed(() => this.determinations().find((d) => d.jurisdiction === this.jurisdiction() && d.status === 'SIGNED') ?? null);
  readonly canSign = computed(() => this.allDecided() && this.session.isSigner() && !this.signed());
  readonly ruleVersion = computed(() => this.ruleVersions().find((r) => r.id === this.selected()?.ruleVersionId) ?? null);

  constructor() {
    this.load();
  }

  private async load(): Promise<void> {
    try {
      const [program, determinations, audit, ruleVersions] = await Promise.all([
        firstValueFrom(this.api.program(this.programId)),
        firstValueFrom(this.api.determinations(this.programId)),
        firstValueFrom(this.api.audit(500)),
        firstValueFrom(this.api.ruleVersions()),
      ]);
      this.program.set(program);
      this.determinations.set(determinations);
      this.ruleVersions.set(ruleVersions);
      const assessmentId = this.api.latestAssessmentId(audit, this.programId);
      if (!assessmentId) {
        this.error.set('No finished assessment exists for this program yet.');
        return;
      }
      this.assessment.set(await firstValueFrom(this.api.assessment(assessmentId)));
      const wanted = this.route.snapshot.queryParamMap.get('jurisdiction');
      await this.selectJurisdiction(wanted && program.jurisdictions.includes(wanted) ? wanted : program.jurisdictions[0]);
    } catch {
      this.error.set('The review could not be loaded.');
    }
  }

  async selectJurisdiction(code: string): Promise<void> {
    this.jurisdiction.set(code);
    this.message.set(null);
    await this.router.navigate([], { relativeTo: this.route, queryParams: { jurisdiction: code }, replaceUrl: true });
    const view = await firstValueFrom(this.api.mappings(this.assessment()!.id, code));
    this.mappings.set(view.mappings);
    this.select(this.firstUndecided()?.id ?? view.mappings[0]?.id ?? null);
  }

  select(id: string | null): void {
    this.selectedId.set(id);
    this.note.set(this.selected()?.decision?.note ?? '');
  }

  firstUndecided(after?: string): Mapping | undefined {
    const list = this.mappings();
    const start = after ? list.findIndex((m) => m.id === after) + 1 : 0;
    return [...list.slice(start), ...list.slice(0, start)].find((m) => !m.decision);
  }

  onTabKey(event: KeyboardEvent, index: number): void {
    const codes = this.program()?.jurisdictions ?? [];
    let next = index;
    if (event.key === 'ArrowRight') { next = (index + 1) % codes.length; }
    else if (event.key === 'ArrowLeft') { next = (index - 1 + codes.length) % codes.length; }
    else if (event.key === 'Home') { next = 0; }
    else if (event.key === 'End') { next = codes.length - 1; }
    else { return; }
    event.preventDefault();
    this.selectJurisdiction(codes[next]);
    (document.getElementById('tab-' + codes[next]) as HTMLElement | null)?.focus();
  }

  async decide(kind: DecisionKind): Promise<void> {
    const current = this.selected();
    if (!current) { return; }
    const note = this.note().trim() || null;
    if (kind === 'GAP_CONFIRMED' && !note) {
      this.error.set('Confirming a gap requires a note explaining why.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const updated = await firstValueFrom(this.api.review(current.id, kind, note));
      this.mappings.update((list) => list.map((m) => (m.id === updated.id ? updated : m)));
      this.message.set(`${updated.requirement.ref}: ${DECISION_LABEL[kind]}.`);
      const next = this.firstUndecided(current.id);
      this.select(next ? next.id : current.id);
    } catch (e: unknown) {
      this.error.set(problemDetail(e) ?? 'The decision could not be recorded.');
    } finally {
      this.busy.set(false);
    }
  }

  async openSignDialog(): Promise<void> {
    const data: SignDialogData = { jurisdiction: this.jurisdiction(), total: this.mappings().length };
    const outcome = await firstValueFrom(this.dialog.open<Outcome | null>(SignDialog, { data, ariaLabel: 'Sign determination' }).closed);
    if (!outcome) { return; }
    this.busy.set(true);
    try {
      const determination = await firstValueFrom(this.api.sign(this.programId, this.jurisdiction(), outcome));
      this.determinations.update((list) => [determination, ...list]);
      this.message.set(`Determination for ${determination.jurisdiction} signed by ${determination.signedByName}: ${OUTCOME_LABEL[determination.outcome]}.`);
    } catch (e: unknown) {
      this.error.set(problemDetail(e) ?? 'The determination could not be signed.');
    } finally {
      this.busy.set(false);
    }
  }

  howChecked(m: Mapping): string {
    if (m.method === 'RULES_ENGINE') {
      return 'Checked by code. The number recorded for the program was compared with the requirement: ' + m.rationale;
    }
    if (!m.evidence) {
      return 'Candidate passages from the program documents were searched and shown to the model. ' + m.rationale;
    }
    return 'The model proposed this passage; the quote was then verified character for character against the stored page text before it was shown. ' + m.rationale;
  }
}

function problemDetail(e: unknown): string | null {
  const err = e as { error?: { detail?: string } };
  return err?.error?.detail ?? null;
}
