import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, linkedSignal, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { phosphorBooksDuotone, phosphorCheckCircleDuotone, phosphorFilesDuotone } from '@ng-icons/phosphor-icons/duotone';
import { phosphorCheckCircleFill, phosphorWarningCircleFill, phosphorXCircleFill } from '@ng-icons/phosphor-icons/fill';
import {
  phosphorCaretDown, phosphorCheck, phosphorClock, phosphorCopy, phosphorFileDoc, phosphorFilePdf, phosphorLockSimple, phosphorPlay, phosphorPlus,
  phosphorSpinner, phosphorUploadSimple, phosphorX,
} from '@ng-icons/phosphor-icons/regular';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { MultiSelect } from 'primeng/multiselect';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { Step, StepList, StepPanel, StepPanels, Stepper } from 'primeng/stepper';
import { licenseLabel } from '../../core/labels';
import { NewProgram } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { registerComponentTokens } from '../../theme/component-tokens';
import { InfoTip } from '../../ui/info-tip';
import { MapState } from '../../ui/state-map/state-map-model';
import { StateMap } from '../../ui/state-map/state-map';
import { CoursesStep } from './courses-step';
import { DocumentsStep } from './documents-step';
import { FactsStep } from './facts-step';
import { ReadinessStep } from './readiness-step';
import { SETUP_STEPS, SetupStep, firstOpenStep, licenseTypes } from './setup-model';
import { SetupNotice, SetupStore } from './setup-store';
import { SETUP_TOKENS } from './setup-theme';

/** The icons the setup workspace and its steps show beyond SHELL_ICONS, provided by its route (setup.routes.ts). */
export const SETUP_ICONS = {
  phosphorBooksDuotone, phosphorCaretDown, phosphorCheck, phosphorCheckCircleDuotone, phosphorCheckCircleFill, phosphorClock, phosphorCopy, phosphorFileDoc,
  phosphorFilePdf, phosphorFilesDuotone, phosphorLockSimple, phosphorPlay, phosphorPlus, phosphorSpinner, phosphorUploadSimple, phosphorWarningCircleFill,
  phosphorX, phosphorXCircleFill,
};

/** The create form of the Details and States steps. */
interface ProgramForm { name: string; licenseType: string; catalogYear: string; jurisdictions: string[] }
type FormField = keyof ProgramForm;
const EMPTY: ProgramForm = { name: '', licenseType: '', catalogYear: '', jurisdictions: [] };

const STEP_LABEL: Record<SetupStep, string> = {
  DETAILS: 'Details', STATES: 'States', COURSES: 'Courses', DOCUMENTS: 'Documents', FACTS: 'Program facts', READINESS: 'Readiness',
};

/**
 * Program setup (F2): the program, its target states, its courses and their catalog years, its documents with their
 * reading status, the program facts the rules engine needs (each backed by a quote selected on its page, product
 * rule 2), a readiness check and the first assessment. One Stepper is the screen's hero. On /programs/new only the
 * create form is open; once the program exists the URL becomes its own and every step is open, for later additions too.
 */
@Component({
  selector: 'lx-setup',
  imports: [
    FormsModule, NgIcon, ButtonDirective, ButtonLabel, InputText, Message, MultiSelect, Select, Skeleton, Step, StepList, StepPanel, StepPanels, Stepper,
    InfoTip, StateMap, CoursesStep, DocumentsStep, FactsStep, ReadinessStep,
  ],
  providers: [SetupStore],
  templateUrl: './setup.html',
  styleUrl: './setup.css',
})
export class SetupWorkspace {
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);
  readonly context = inject(ProgramContext);
  readonly store = inject(SetupStore);

  /** Absent on /programs/new. */
  readonly programId = input<string>();

  readonly steps = SETUP_STEPS;
  /** A panel is not a tabpanel here (see the template); its region inside is named by its step's button. */
  readonly panelPt = { root: { role: null, 'aria-controls': null } };
  readonly label = STEP_LABEL;
  readonly license = licenseLabel;
  readonly form = signal<ProgramForm>({ ...EMPTY });
  readonly problems = signal<Partial<Record<FormField, string>>>({});
  readonly general = signal<string | null>(null);
  readonly busy = signal(false);
  /** The polite live region (data-testid="message"). */
  readonly message = signal<string | null>(null);

  readonly licenseOptions = computed(() => licenseTypes(this.store.ruleVersions()));
  readonly stateOptions = computed(() => this.store.jurisdictions().map((j) => ({ code: j.code, label: `${j.name} (${j.code})` })));
  /** The states as the mini map draws them: the chosen ones (or the program's) filled in accent, a picture beside the MultiSelect. */
  readonly mapStates = computed<MapState[]>(() => (this.store.program()?.jurisdictions ?? this.form().jurisdictions)
    .map((code) => ({ code, tile: 'IN_REVIEW', reviewed: 1, total: 1, status: 'Target state', action: null })));
  readonly stateNames = computed(() => {
    const names = new Map(this.store.jurisdictions().map((j) => [j.code, j.name]));
    return (this.store.program()?.jurisdictions ?? []).map((code) => ({ code, name: names.get(code) ?? code }));
  });

  /** The open step: the first one with work left once the program has loaded, Details on a new program. */
  readonly step = linkedSignal<boolean, SetupStep>({
    source: () => this.store.loaded(),
    // Read once when the program has loaded, not on every later change: adding a course must not move the person on.
    computation: (loaded, previous) => untracked(() => {
      const readiness = this.store.readiness();
      if (!loaded || !readiness) { return previous?.value ?? (this.store.programId() ? 'COURSES' : 'DETAILS'); }
      return firstOpenStep(this.store.courses() ?? [], this.store.setupDocuments(), readiness);
    }),
  });
  readonly stepValue = computed(() => SETUP_STEPS.indexOf(this.step()) + 1);
  readonly summaries = computed<Record<SetupStep, string>>(() => this.summarise());

  constructor() {
    registerComponentTokens(SETUP_TOKENS);
    effect(() => {
      const id = this.programId() ?? null;
      untracked(() => {
        this.store.programId.set(id);
        if (id) { this.context.ensure(id); }
      });
    });
  }

  /** Every step is open once the program exists; before that only the two that create it. */
  open(step: SetupStep): boolean {
    return this.store.programId() !== null || step === 'DETAILS' || step === 'STATES';
  }

  done(step: SetupStep): boolean {
    const readiness = this.store.readiness();
    switch (step) {
      case 'DETAILS': case 'STATES': return this.store.program() !== null;
      case 'COURSES': return (this.store.courses()?.length ?? 0) > 0;
      case 'DOCUMENTS': return (this.store.documents()?.length ?? 0) > 0 && !!readiness?.checks.slice(1, 3).every((c) => c.ok);
      case 'FACTS': return readiness?.checks[3].ok ?? false;
      case 'READINESS': return this.store.run()?.view.status === 'FINISHED';
    }
  }

  go(value: number | undefined): void {
    const step = value ? SETUP_STEPS[value - 1] : undefined;
    if (step && this.open(step)) { this.step.set(step); }
  }

  set<K extends FormField>(field: K, value: ProgramForm[K]): void {
    this.form.update((f) => ({ ...f, [field]: value }));
    if (this.problems()[field]) { this.problems.update((p) => ({ ...p, [field]: undefined })); }
  }

  /** Details are checked before moving on to States; everything the server would refuse for being empty or too long. */
  next(): void {
    const problems = detailProblems(this.form());
    this.problems.set(problems);
    if (Object.keys(problems).length === 0) { this.step.set('STATES'); }
  }

  async create(): Promise<void> {
    const problems = { ...detailProblems(this.form()), ...stateProblems(this.form()) };
    this.problems.set(problems);
    this.general.set(null);
    if (problems.name || problems.licenseType || problems.catalogYear) { this.step.set('DETAILS'); }
    if (Object.keys(problems).length > 0 || this.busy()) { return; }
    this.busy.set(true);
    try {
      const f = this.form();
      const body: NewProgram = { name: f.name.trim(), licenseType: f.licenseType.trim(), catalogYear: f.catalogYear.trim(), jurisdictions: f.jurisdictions };
      const program = await this.store.create(body);
      const text = `${program.name} created with ${program.jurisdictions.length} ${program.jurisdictions.length === 1 ? 'state' : 'states'}.`;
      this.announce({ summary: 'Program created', detail: text });
      this.form.set({ ...EMPTY });
      await this.router.navigate(['/programs', program.id, 'setup'], { replaceUrl: true });
    } catch (e: unknown) {
      const detail = problemDetail(e, 'The program could not be created.');
      if (e instanceof HttpErrorResponse && e.status === 400 && /jurisdiction/i.test(detail)) {
        this.problems.set({ jurisdictions: detail });
      } else {
        this.general.set(detail);
      }
    } finally {
      this.busy.set(false);
    }
  }

  /** A step finished something: a toast says it on screen, the hidden live region to assistive technology. */
  announce(notice: SetupNotice): void {
    this.message.set(`${notice.summary}. ${notice.detail}`);
    this.toast.add({ severity: notice.severity ?? 'success', summary: notice.summary, detail: notice.detail });
  }

  private summarise(): Record<SetupStep, string> {
    const program = this.store.program();
    const courses = this.store.courses()?.length ?? 0;
    const documents = this.store.setupDocuments();
    const reading = documents.filter((d) => d.state === 'READING').length;
    const readiness = this.store.readiness();
    const missing = readiness?.checks[3].items.length ?? 0;
    const blocking = readiness?.checks.filter((c) => c.blocking && !c.ok).length ?? 0;
    const chosen = this.form().jurisdictions.length;
    return {
      DETAILS: program ? 'Fixed' : 'Name and license',
      STATES: program ? plural(program.jurisdictions.length, 'state') : chosen ? `${chosen} chosen` : 'None chosen',
      COURSES: courses ? plural(courses, 'course') : 'None yet',
      DOCUMENTS: reading ? `${reading} still reading` : documents.length ? plural(documents.length, 'document') : 'None yet',
      FACTS: !readiness?.fieldsUsed.length ? 'None needed' : missing ? `${missing} missing` : 'All recorded',
      READINESS: this.store.run()?.view.status === 'FINISHED' ? 'Assessed' : !readiness ? 'Not yet' : blocking ? `${blocking} to fix` : 'Ready',
    };
  }
}

const plural = (n: number, noun: string): string => `${n} ${noun}${n === 1 ? '' : 's'}`;

function detailProblems(f: ProgramForm): Partial<Record<FormField, string>> {
  const problems: Partial<Record<FormField, string>> = {};
  if (!f.name.trim()) { problems.name = 'Enter the program name.'; } else if (f.name.trim().length > 200) { problems.name = 'The name has at most 200 characters.'; }
  if (!f.licenseType.trim()) { problems.licenseType = 'Choose or enter the license type.'; } else if (f.licenseType.trim().length > 100) { problems.licenseType = 'The license type has at most 100 characters.'; }
  if (!f.catalogYear.trim()) { problems.catalogYear = 'Enter the catalog year.'; } else if (f.catalogYear.trim().length > 20) { problems.catalogYear = 'The catalog year has at most 20 characters.'; }
  return problems;
}

function stateProblems(f: ProgramForm): Partial<Record<FormField, string>> {
  return f.jurisdictions.length === 0 ? { jurisdictions: 'Choose at least one state.' } : {};
}
