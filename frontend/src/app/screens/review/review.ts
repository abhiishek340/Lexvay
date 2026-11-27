import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Message } from 'primeng/message';
import { ProgressBar } from 'primeng/progressbar';
import { RadioButton } from 'primeng/radiobutton';
import { SelectButton } from 'primeng/selectbutton';
import { Skeleton } from 'primeng/skeleton';
import { Splitter, SplitterPanel } from 'primeng/splitter';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Textarea } from 'primeng/textarea';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { DECISION_LABEL, OUTCOME_LABEL } from '../../core/labels';
import { DecisionKind, Mapping, Outcome } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { SessionService } from '../../core/session.service';
import { DecisionState, FindingLabel } from '../../ui/finding';
import { REVIEW_FILTERS, ReviewFilter, ReviewWorkspace, SHORTCUT_HELP, ShortcutCommand, decisionBlocker, shortcutFor } from './review-workspace';

const WIDE = '(min-width: 1024px)';

@Component({
  selector: 'lx-review',
  imports: [DatePipe, NgTemplateOutlet, FormsModule, NgIcon, ButtonDirective, ButtonLabel, Dialog, Message, ProgressBar, RadioButton, SelectButton, Skeleton,
    Splitter, SplitterPanel, Tab, TabList, TabPanel, TabPanels, Tabs, Textarea, FindingLabel, DecisionState],
  templateUrl: './review.html',
  styleUrl: './review.css',
  host: { '(document:keydown)': 'onKey($event)' },
})
export class Review {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);
  readonly session = inject(SessionService);
  readonly context = inject(ProgramContext);

  readonly programId = input.required<string>();
  readonly workspace = new ReviewWorkspace();
  readonly filters = REVIEW_FILTERS;
  readonly shortcuts = SHORTCUT_HELP;
  readonly decisionLabel = DECISION_LABEL;
  readonly outcomeLabel = OUTCOME_LABEL;
  readonly outcomes: Outcome[] = ['MEETS', 'DOES_NOT_MEET', 'NOT_DETERMINED'];

  readonly jurisdiction = signal<string>('');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** The polite live region (data-testid="message"): the last result in words. */
  readonly message = signal<string | null>(null);
  readonly note = signal('');
  readonly noteError = signal<string | null>(null);
  readonly busy = signal(false);
  readonly signOpen = signal(false);
  readonly helpOpen = signal(false);
  readonly outcome = signal<Outcome | null>(null);
  readonly wide = signal(typeof matchMedia === 'function' ? matchMedia(WIDE).matches : true);

  private readonly noteField = viewChild<ElementRef<HTMLTextAreaElement>>('noteField');
  private readonly listPane = viewChild<ElementRef<HTMLElement>>('listPane');

  readonly selected = this.workspace.selected;
  readonly signed = computed(() => this.context.determinations().find((d) => d.jurisdiction === this.jurisdiction() && d.status === 'SIGNED') ?? null);
  readonly reopened = computed(() => !this.signed() && this.context.determinations().some((d) => d.jurisdiction === this.jurisdiction() && d.status === 'REOPENED'));
  readonly canSign = computed(() => this.workspace.allDecided() && this.session.isSigner() && !this.signed());
  readonly progressPercent = computed(() => (this.workspace.total() ? Math.round((this.workspace.decided() / this.workspace.total()) * 100) : 0));
  readonly ruleVersion = computed(() => this.context.ruleVersions().find((r) => r.id === this.selected()?.ruleVersionId) ?? null);
  readonly paneStateKey = computed(() => `lexvay.review.panes.${this.session.user()?.id ?? 'anonymous'}`);
  readonly signHelp = computed(() => {
    if (!this.session.isSigner()) { return 'Only a signer can sign.'; }
    if (!this.workspace.allDecided()) { return 'Enabled when every requirement has a decision.'; }
    return 'Every requirement has a decision.';
  });

  constructor() {
    effect(() => {
      const id = this.programId();
      untracked(() => this.context.ensure(id));
    });
    // Once the program and its latest assessment are known, open the requested (or first) jurisdiction.
    effect(() => {
      const program = this.context.program();
      const assessment = this.context.assessment();
      if (!program || !this.context.ready() || untracked(this.jurisdiction)) { return; }
      if (!assessment) {
        untracked(() => this.error.set('No finished assessment exists for this program yet. Requirements appear here after one runs.'));
        return;
      }
      const wanted = this.route.snapshot.queryParamMap.get('jurisdiction');
      untracked(() => this.selectJurisdiction(wanted && program.jurisdictions.includes(wanted) ? wanted : program.jurisdictions[0]));
    });
    if (typeof matchMedia === 'function') {
      const media = matchMedia(WIDE);
      const onChange = (e: MediaQueryListEvent): void => this.wide.set(e.matches);
      media.addEventListener('change', onChange);
      inject(DestroyRef).onDestroy(() => media.removeEventListener('change', onChange));
    }
  }

  async selectJurisdiction(code: string | number | undefined): Promise<void> {
    const assessment = this.context.assessment();
    if (typeof code !== 'string' || !assessment || code === this.jurisdiction()) { return; }
    this.jurisdiction.set(code);
    this.message.set(null);
    this.error.set(null);
    this.loading.set(true);
    this.router.navigate([], { relativeTo: this.route, queryParams: { jurisdiction: code }, replaceUrl: true });
    try {
      const view = await firstValueFrom(this.api.mappings(assessment.id, code));
      if (this.jurisdiction() === code) {
        this.workspace.load(view.mappings);
        this.resetNote();
      }
    } catch (e: unknown) {
      this.error.set(problemDetail(e, `The requirements for ${code} could not be loaded.`));
    } finally {
      this.loading.set(false);
    }
  }

  count(filter: ReviewFilter): number {
    return this.workspace.counts()[filter];
  }

  select(id: string): void {
    this.workspace.select(id);
    this.resetNote();
  }

  setFilter(filter: ReviewFilter | null): void {
    if (filter) {
      this.workspace.setFilter(filter);
      this.resetNote();
    }
  }

  async decide(kind: DecisionKind): Promise<void> {
    const current = this.selected();
    if (!current || this.busy() || !this.session.canReview()) { return; }
    const blocker = decisionBlocker(kind, this.note());
    if (blocker) {
      this.noteError.set(blocker);
      this.noteField()?.nativeElement.focus();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const updated = await firstValueFrom(this.api.review(current.id, kind, this.note().trim() || null));
      const next = this.workspace.applyDecision(updated);
      this.resetNote();
      const done = `${updated.requirement.ref}: ${DECISION_LABEL[kind]}.`;
      const then = next && next.id !== updated.id ? ` Now showing ${next.requirement.ref}.` : '';
      this.message.set(done + then + ` ${this.workspace.progressLabel()}.`);
      this.toast.add({ severity: 'success', summary: `${DECISION_LABEL[kind]}: ${updated.requirement.ref}`, detail: this.workspace.progressLabel() });
      this.scrollSelectedIntoView();
      this.context.refreshAssessment();
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The decision could not be recorded.'));
    } finally {
      this.busy.set(false);
    }
  }

  openSign(): void {
    this.outcome.set(null);
    this.signOpen.set(true);
  }

  async sign(): Promise<void> {
    const outcome = this.outcome();
    if (!outcome || this.busy()) { return; }
    this.busy.set(true);
    try {
      const determination = await firstValueFrom(this.api.sign(this.programId(), this.jurisdiction(), outcome));
      this.signOpen.set(false);
      await this.context.refreshDeterminations();
      const text = `Determination for ${determination.jurisdiction} signed by ${determination.signedByName}: ${OUTCOME_LABEL[determination.outcome]}.`;
      this.message.set(text);
      this.toast.add({ severity: 'success', summary: 'Determination signed', detail: text });
    } catch (e: unknown) {
      this.signOpen.set(false);
      this.error.set(problemDetail(e, 'The determination could not be signed.'));
    } finally {
      this.busy.set(false);
    }
  }

  onKey(event: KeyboardEvent): void {
    if (this.signOpen() || this.helpOpen()) { return; }
    const target = event.target as HTMLElement | null;
    const command = shortcutFor({
      key: event.key, ctrlKey: event.ctrlKey, metaKey: event.metaKey, altKey: event.altKey,
      targetTag: target?.tagName, targetEditable: target?.isContentEditable,
    });
    if (!command || !this.selected()) { return; }
    event.preventDefault();
    this.run(command);
  }

  private run(command: ShortcutCommand): void {
    switch (command) {
      case 'next':
      case 'previous':
        this.workspace.move(command === 'next' ? 1 : -1);
        this.resetNote();
        this.scrollSelectedIntoView();
        break;
      case 'accept': this.decide('ACCEPTED'); break;
      case 'request': this.decide('EVIDENCE_REQUESTED'); break;
      case 'gap': this.noteField()?.nativeElement.focus(); break;
      case 'help': this.helpOpen.set(true); break;
    }
  }

  howChecked(m: Mapping): { icon: 'phosphorCalculator' | 'phosphorMagnifyingGlass'; title: string; text: string } {
    if (m.method === 'RULES_ENGINE') {
      return { icon: 'phosphorCalculator', title: 'Rules engine calculation',
        text: 'The number recorded for the program was compared with the requirement by code, not by the model.' };
    }
    if (!m.evidence) {
      return { icon: 'phosphorMagnifyingGlass', title: 'Search and model proposal',
        text: 'Candidate passages from the program documents were searched and shown to the model, which proposed no passage that answers this requirement.' };
    }
    return { icon: 'phosphorMagnifyingGlass', title: 'Model proposal, quote verified',
      text: 'The model proposed this passage. The quote was then verified character for character against the stored page text before it was shown here.' };
  }

  noEvidenceReason(m: Mapping): string {
    return m.method === 'RULES_ENGINE'
      ? 'This requirement is a number. The rules engine checked it against the program facts, so there is no passage to quote.'
      : 'No passage in the program documents answers this requirement.';
  }

  private resetNote(): void {
    this.note.set('');
    this.noteError.set(null);
  }

  private scrollSelectedIntoView(): void {
    requestAnimationFrame(() => this.listPane()?.nativeElement.querySelector('[aria-current="true"]')?.scrollIntoView({ block: 'nearest' }));
  }
}
