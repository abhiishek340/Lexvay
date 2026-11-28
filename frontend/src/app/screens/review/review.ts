import { Component, DestroyRef, ElementRef, afterRenderEffect, computed, effect, inject, input, linkedSignal, signal, untracked, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import {
  phosphorArrowSquareOut, phosphorCalculator, phosphorCaretRight, phosphorChatCircleText, phosphorCheckCircle, phosphorCircleDashed,
  phosphorKeyboard, phosphorListBullets, phosphorQuestion, phosphorSeal, phosphorUser, phosphorXCircle,
} from '@ng-icons/phosphor-icons/regular';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { DECISION_LABEL, OUTCOME_LABEL } from '../../core/labels';
import { DecisionKind, Determination, Outcome } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { paletteShortcut } from '../../ui/command-palette/shortcut';
import { DecisionBar } from '../../ui/decision-bar/decision-bar';
import { Help, HelpTerm } from '../../ui/help';
import { Meter } from '../../ui/meter';
import { Seal } from '../../ui/seal';
import { StateTab, StateTabs } from '../../ui/state-tabs';
import { EvidencePane } from './evidence-pane';
import { RequirementCard } from './requirement-card';
import { RequirementList } from './requirement-list';
import { REVIEW_TOKENS } from './review-theme';
import { ReviewFilter, ReviewWorkspace, SHORTCUT_HELP, ShortcutCommand, decisionBlocker, shortcutFor, stateProgress } from './review-workspace';
import { SignDialog } from './sign-dialog';

const WIDE = '(min-width: 1024px)';

/** The icons the Review workspace and its own parts show beyond SHELL_ICONS, provided by its route (review.routes.ts). */
export const REVIEW_ICONS = {
  phosphorArrowSquareOut, phosphorCalculator, phosphorCaretRight, phosphorChatCircleText, phosphorCheckCircle, phosphorCircleDashed,
  phosphorKeyboard, phosphorListBullets, phosphorQuestion, phosphorSeal, phosphorUser, phosphorXCircle,
};

/** Review's definitions, behind its one Help button. */
const HELP: readonly HelpTerm[] = [
  { term: 'Proposed finding', definition: 'What the documents suggest. A person accepts it, requests evidence, or confirms a gap.' },
  { term: 'Signing', definition: 'Once every requirement is decided, a signer signs the determination for the state.' },
  { term: 'Reopened', definition: 'A change reopened the signed determination; it needs a new signature.' },
  { term: 'View only', definition: 'Your role can read this review but not record decisions.' },
];

/**
 * Review (DESIGN.md "Review"), the screen people work in for hours. A header with the states as an underlined tab row,
 * the Meter and, once every requirement is decided, "Sign <state>"; below it the requirement list, the requirement with
 * the DecisionBar at its foot, and the evidence on its page. A decision never holds input back; the next requirement
 * follows at once.
 */
@Component({
  selector: 'lx-review',
  imports: [NgIcon, ButtonDirective, ButtonLabel, Dialog, Message, Skeleton, TabPanel, TabPanels, Tabs,
    DecisionBar, Help, Meter, Seal, StateTabs, EvidencePane, RequirementCard, RequirementList, SignDialog],
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
  /** Query parameters from links and the command palette: the state to open and a requirement (by ref) to select. */
  readonly jurisdictionParam = input<string | undefined>(undefined, { alias: 'jurisdiction' });
  readonly ref = input<string | undefined>();
  readonly workspace = new ReviewWorkspace();
  readonly shortcuts = SHORTCUT_HELP;
  /** The command palette works everywhere; the list names it beside the review keys. */
  readonly paletteKeys = paletteShortcut(typeof navigator === 'undefined' ? '' : navigator.platform);
  readonly outcomeLabel = OUTCOME_LABEL;
  readonly help = HELP;

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
  /** The determination the signing dialog has just recorded, for its seal. */
  readonly justSigned = signal<Determination | null>(null);
  readonly wide = signal(typeof matchMedia === 'function' ? matchMedia(WIDE).matches : true);

  /** A jurisdiction asked for before the assessment was known. */
  private requested: string | null = null;
  /** A requirement asked for by ref, selected once its jurisdiction's list has loaded. */
  private requestedRef: string | null = null;
  /**
   * The states this screen is writing into the URL right now. Their query parameter comes back as an input; an echo
   * that lands after the person has already moved on to another state must not pull the screen back.
   */
  private readonly ownNavigations = new Set<string>();
  private readonly bar = viewChild(DecisionBar);
  private readonly centre = viewChild<ElementRef<HTMLElement>>('centre');

  readonly selected = this.workspace.selected;
  readonly evidence = this.workspace.evidence;
  /** The requirement card, keyed by mapping so a new requirement enters (from the side it comes from). */
  readonly hero = computed(() => {
    const m = this.selected();
    return m ? [m] : [];
  });
  readonly enterFrom = computed(() => (this.workspace.travel() === 'back' ? 'lx-enter-back' : 'lx-enter-forward'));
  /**
   * The note opens by itself where the reviewer is asked which evidence they relied on (a person answered an evidence
   * request, F4); otherwise it waits for Gap, G or the note button. Every requirement starts from that again.
   */
  private readonly answered = computed(() => this.evidence()?.answered ?? false);
  readonly noteOpen = linkedSignal(() => {
    this.workspace.selectedId();
    return this.answered();
  });
  /** Mapping ids whose responses are being fetched, so a selection never asks twice. */
  private readonly fetching = new Set<string>();
  readonly stateName = computed(() => this.context.jurisdictionName(this.jurisdiction()));
  readonly tabs = computed<StateTab[]>(() => {
    const progress = stateProgress(this.context.assessment()?.jurisdictions ?? []);
    return (this.context.program()?.jurisdictions ?? []).map((code) => ({ code, name: this.context.jurisdictionName(code), progress: progress.get(code) ?? null }));
  });
  readonly signed = computed(() => this.context.determinations().find((d) => d.jurisdiction === this.jurisdiction() && d.status === 'SIGNED') ?? null);
  readonly reopened = computed(() => !this.signed() && this.context.determinations().some((d) => d.jurisdiction === this.jurisdiction() && d.status === 'REOPENED'));
  readonly ruleVersion = computed(() => this.context.ruleVersions().find((r) => r.id === this.selected()?.ruleVersionId) ?? null);

  constructor() {
    registerComponentTokens(REVIEW_TOKENS);
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
      const wanted = this.requested ?? this.route.snapshot.queryParamMap.get('jurisdiction');
      untracked(() => this.selectJurisdiction(wanted && program.jurisdictions.includes(wanted) ? wanted : program.jurisdictions[0]));
    });
    // A later link to another state or requirement while the screen is open (the command palette, the work queue).
    effect(() => {
      const code = this.jurisdictionParam();
      const ref = this.ref();
      untracked(() => {
        if (ref) { this.requestedRef = ref; }
        if (code && this.jurisdiction() && code !== this.jurisdiction() && !this.ownNavigations.has(code)) {
          this.selectJurisdiction(code);
        } else if (this.workspace.mappings().length > 0) {
          this.selectRequested();
        }
      });
    });
    // Evidence people provided is fetched with the selected mapping, once per mapping (and again when more arrived).
    effect(() => {
      const m = this.selected();
      if (m && this.workspace.evidence()?.providedLoading) {
        untracked(() => this.fetchResponses(m.id));
      }
    });
    // A new requirement is read from its top: the centre pane scrolls back up when the selection changes.
    afterRenderEffect(() => {
      this.workspace.selectedId();
      const centre = this.centre()?.nativeElement;
      if (centre && this.wide()) { centre.scrollTop = 0; }
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
    if (typeof code !== 'string' || code === this.jurisdiction()) { return; }
    if (!assessment || !this.context.ready()) {
      // A tab chosen before the program finished loading is opened once it has.
      this.requested = code;
      return;
    }
    this.jurisdiction.set(code);
    this.workspace.load([]);
    this.message.set(null);
    this.error.set(null);
    this.loading.set(true);
    this.ownNavigations.add(code);
    this.router.navigate([], { relativeTo: this.route, queryParams: { jurisdiction: code }, replaceUrl: true })
      .finally(() => this.ownNavigations.delete(code));
    try {
      const view = await firstValueFrom(this.api.mappings(assessment.id, code));
      if (this.jurisdiction() === code) {
        this.workspace.load(view.mappings);
        this.resetNote();
        this.selectRequested();
      }
    } catch (e: unknown) {
      this.error.set(problemDetail(e, `The requirements for ${code} could not be loaded.`));
    } finally {
      this.loading.set(false);
    }
  }

  private selectRequested(): void {
    const ref = this.requestedRef;
    this.requestedRef = null;
    const mapping = ref ? this.workspace.mappings().find((m) => m.requirement.ref === ref) : undefined;
    if (mapping) {
      this.select(mapping.id);
    }
  }

  select(id: string): void {
    this.workspace.select(id);
    this.resetNote();
  }

  setFilter(filter: ReviewFilter): void {
    this.workspace.setFilter(filter);
    this.resetNote();
  }

  async decide(kind: DecisionKind): Promise<void> {
    const current = this.selected();
    if (!current || this.busy() || !this.session.canReview()) { return; }
    const blocker = decisionBlocker(kind, this.note());
    if (blocker) {
      this.noteError.set(blocker);
      this.bar()?.openNote();
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
      this.context.refreshAssessment();
      // A request opens or an answered one is decided: the inbox and its badge follow.
      this.context.refreshEvidenceRequests();
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The decision could not be recorded.'));
    } finally {
      this.busy.set(false);
    }
  }

  openSign(): void {
    this.justSigned.set(null);
    this.signOpen.set(true);
  }

  async sign(outcome: Outcome): Promise<void> {
    if (this.busy()) { return; }
    this.busy.set(true);
    try {
      const determination = await firstValueFrom(this.api.sign(this.programId(), this.jurisdiction(), outcome));
      // The dialog stays open on its seal until the signer closes it.
      this.justSigned.set(determination);
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
        break;
      case 'accept': this.decide('ACCEPTED'); break;
      case 'request': this.decide('EVIDENCE_REQUESTED'); break;
      case 'gap': this.bar()?.openNote(); break;
      case 'help': this.helpOpen.set(true); break;
    }
  }

  private async fetchResponses(mappingId: string): Promise<void> {
    if (this.fetching.has(mappingId)) { return; }
    this.fetching.add(mappingId);
    try {
      this.workspace.setResponses(mappingId, await firstValueFrom(this.api.evidenceResponses(mappingId)));
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The evidence provided for this requirement could not be loaded.'));
    } finally {
      this.fetching.delete(mappingId);
    }
  }

  private resetNote(): void {
    this.note.set('');
    this.noteError.set(null);
    this.noteOpen.set(this.answered());
  }
}
