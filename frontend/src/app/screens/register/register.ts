import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { phosphorArrowCounterClockwise, phosphorCheckCircle, phosphorCopy, phosphorDownloadSimple, phosphorMinusCircle, phosphorPrinter, phosphorSealCheck, phosphorXCircle } from '@ng-icons/phosphor-icons/regular';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Tag } from 'primeng/tag';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { OUTCOME_LABEL, STATUS_LABEL } from '../../core/labels';
import { DeterminationLists, DeterminationStatus, Jurisdiction, Outcome, Program, RegisterFilter, RegisterRow } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { NO_FILTER, registerCsvUrl } from '../../core/register';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { IconName } from '../../theme/icons';
import { Enter } from '../../ui/enter';
import { InfoTip } from '../../ui/info-tip';
import { Seal } from '../../ui/seal';
import { Stat } from '../../ui/stat';
import { StateMap } from '../../ui/state-map/state-map';
import { REGISTER_TOKENS } from './register-theme';
import { DISCLOSURE_NOTE, disclosureSections, registerMapStates } from './register-view';

type TabId = 'register' | 'by-program';
type Severity = 'success' | 'danger' | 'secondary' | 'warn';
interface TagView { severity: Severity; label: string; icon: IconName }

const OUTCOMES: Outcome[] = ['MEETS', 'DOES_NOT_MEET', 'NOT_DETERMINED'];
/** The icons the register shows beyond SHELL_ICONS, provided by its route (register.routes.ts). */
export const REGISTER_ICONS = { phosphorArrowCounterClockwise, phosphorCheckCircle, phosphorCopy, phosphorDownloadSimple, phosphorMinusCircle, phosphorPrinter, phosphorSealCheck, phosphorXCircle };

/** A distinct shape per signed outcome, so the outcome never depends on colour alone. */
const OUTCOME_ICON: Record<Outcome, IconName> = { MEETS: 'phosphorCheckCircle', DOES_NOT_MEET: 'phosphorXCircle', NOT_DETERMINED: 'phosphorMinusCircle' };
const STATUS_ICON: Record<DeterminationStatus, IconName> = { SIGNED: 'phosphorSealCheck', REOPENED: 'phosphorArrowCounterClockwise' };
const OUTCOME_SEVERITY: Record<Outcome, Severity> = { MEETS: 'success', DOES_NOT_MEET: 'danger', NOT_DETERMINED: 'secondary' };
const STATUS_SEVERITY: Record<DeterminationStatus, Severity> = { SIGNED: 'success', REOPENED: 'warn' };

/**
 * The tenant-wide record of signed determinations (F8): the register of every signature with the versions it names,
 * and per program the three lists of 34 CFR 668.43. It shows what people signed and never infers an outcome.
 */
@Component({
  selector: 'lx-register',
  imports: [DatePipe, FormsModule, RouterLink, NgIcon, ButtonDirective, ButtonLabel, Message, Select, Skeleton, TableModule,
    Tab, TabList, TabPanel, TabPanels, Tabs, Tag, ToggleSwitch, Enter, InfoTip, Seal, Stat, StateMap],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);
  private readonly context = inject(ProgramContext);
  readonly session = inject(SessionService);

  readonly outcomeOptions = OUTCOMES.map((value) => ({ value, label: OUTCOME_LABEL[value] }));
  readonly disclosureNote = DISCLOSURE_NOTE;

  readonly tab = signal<TabId>('register');
  readonly programs = signal<Program[] | null>(null);
  private readonly jurisdictions = signal<Jurisdiction[]>([]);
  readonly error = signal<string | null>(null);

  readonly filter = signal<RegisterFilter>(NO_FILTER);
  readonly rows = signal<RegisterRow[] | null>(null);
  readonly rowsError = signal<string | null>(null);
  readonly loadedAt = signal<Date | null>(null);
  /** The last rows that arrived: the map and the count stay in place while a new filter loads. */
  private readonly mapRows = signal<RegisterRow[] | null>(null);
  readonly shownCount = computed(() => {
    const rows = this.mapRows();
    return rows ? { value: rows.length } : null;
  });
  readonly mapStates = computed(() => {
    const rows = this.mapRows();
    return rows ? registerMapStates(rows) : null;
  });
  readonly filtered = computed(() => {
    const f = this.filter();
    return f.programId !== null || f.jurisdiction !== null || f.outcome !== null || f.includeReopened;
  });
  private request = 0;

  readonly listsProgramId = signal<string | null>(null);
  readonly lists = signal<DeterminationLists | null>(null);
  readonly listsError = signal<string | null>(null);

  readonly csvUrl = computed(() => registerCsvUrl(this.filter()));
  readonly sections = computed(() => {
    const lists = this.lists();
    return lists ? disclosureSections(lists) : [];
  });
  readonly programOptions = computed(() => (this.programs() ?? []).map((p) => ({ value: p.id, label: `${p.name}, ${p.catalogYear}` })));
  /** Every jurisdiction some program targets, by code, named. */
  readonly jurisdictionOptions = computed(() => {
    const names = new Map(this.jurisdictions().map((j) => [j.code, j.name]));
    const codes = [...new Set((this.programs() ?? []).flatMap((p) => p.jurisdictions))].sort();
    return codes.map((code) => ({ value: code, label: `${names.get(code) ?? code} (${code})` }));
  });

  /** The print header names what is printed: the program of the open list, or the register's program filter. */
  readonly printProgram = computed(() => {
    if (this.tab() === 'by-program') {
      return this.lists()?.program.name ?? '';
    }
    const id = this.filter().programId;
    return id ? (this.programs()?.find((p) => p.id === id)?.name ?? '') : 'All programs';
  });
  readonly printGeneratedAt = computed(() => (this.tab() === 'by-program' ? this.lists()?.generatedAt ?? null : this.loadedAt()));

  constructor() {
    registerComponentTokens(REGISTER_TOKENS);
    Promise.all([firstValueFrom(this.api.programs()), firstValueFrom(this.api.jurisdictions())])
      .then(([programs, jurisdictions]) => {
        this.jurisdictions.set(jurisdictions);
        this.programs.set(programs);
        const current = this.context.program()?.id;
        const first = programs.find((p) => p.id === current) ?? programs[0];
        if (first) { this.selectListsProgram(first.id); }
      })
      .catch((e: unknown) => this.error.set(problemDetail(e, 'The programs could not be loaded.')));
    this.loadRows();
  }

  /** Outcome and status as a tag: icon and word as well as colour. */
  outcomeTag(outcome: Outcome): TagView {
    return { severity: OUTCOME_SEVERITY[outcome], label: OUTCOME_LABEL[outcome], icon: OUTCOME_ICON[outcome] };
  }

  statusTag(status: DeterminationStatus): TagView {
    return { severity: STATUS_SEVERITY[status], label: STATUS_LABEL[status], icon: STATUS_ICON[status] };
  }

  selectTab(value: string | number | undefined): void {
    this.tab.set(value === 'by-program' ? 'by-program' : 'register');
  }

  setFilter(patch: Partial<RegisterFilter>): void {
    this.filter.update((f) => ({ ...f, ...patch }));
    this.loadRows();
  }

  clearFilters(): void {
    this.filter.set(NO_FILTER);
    this.loadRows();
  }

  private async loadRows(): Promise<void> {
    const request = ++this.request;
    this.rows.set(null);
    this.rowsError.set(null);
    try {
      const rows = await firstValueFrom(this.api.register(this.filter()));
      if (request === this.request) {
        this.rows.set(rows);
        this.mapRows.set(rows);
        this.loadedAt.set(new Date());
      }
    } catch (e: unknown) {
      if (request === this.request) { this.rowsError.set(problemDetail(e, 'The register could not be loaded.')); }
    }
  }

  async selectListsProgram(programId: string): Promise<void> {
    this.listsProgramId.set(programId);
    this.lists.set(null);
    this.listsError.set(null);
    try {
      const lists = await firstValueFrom(this.api.determinationLists(programId));
      if (this.listsProgramId() === programId) { this.lists.set(lists); }
    } catch (e: unknown) {
      this.listsError.set(problemDetail(e, 'The lists for this program could not be loaded.'));
    }
  }

  open(row: RegisterRow): void {
    this.router.navigate(['/programs', row.programId, 'report'], { queryParams: { jurisdiction: row.jurisdictionCode } });
  }

  async copy(hash: string, event: Event): Promise<void> {
    event.stopPropagation();
    try {
      await navigator.clipboard.writeText(hash);
      this.toast.add({ severity: 'success', summary: 'Copied', detail: 'The full versions hash is on the clipboard.' });
    } catch {
      this.toast.add({ severity: 'error', summary: 'The versions hash could not be copied', detail: 'Select it in the evidence report instead.' });
    }
  }

  print(): void {
    window.print();
  }
}
