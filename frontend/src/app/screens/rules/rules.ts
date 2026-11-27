import { Component, DestroyRef, ElementRef, computed, effect, inject, input, linkedSignal, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { phosphorArchive, phosphorArrowCounterClockwise, phosphorArrowDown, phosphorArrowSquareOut, phosphorArrowUp, phosphorCircleDashed, phosphorEquals, phosphorFloppyDisk, phosphorMinusCircle, phosphorNotePencil, phosphorPencilSimple, phosphorPlus, phosphorPlusCircle, phosphorSealCheck, phosphorSpinner, phosphorTrash, phosphorWarningCircle } from '@ng-icons/phosphor-icons/regular';
import { phosphorListChecksDuotone, phosphorScalesDuotone, phosphorSealCheckDuotone, phosphorSpinnerDuotone } from '@ng-icons/phosphor-icons/duotone';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Listbox } from 'primeng/listbox';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { UserSummary } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { RuleVersionRow, anyExtracting } from '../../core/rules';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { Enter } from '../../ui/enter';
import { InfoTip } from '../../ui/info-tip';
import { NewRuleVersionDialog } from './new-rule-version';
import { Notice, RuleVersionDetail } from './rule-version-detail';
import { RULES_TOKENS } from './rules-theme';
import { RULE_STATUS } from './rule-status';
import { UnsavedChanges, leaveNeedsConfirmation } from './unsaved-changes';

/** The icons the Rules screen and its parts show beyond SHELL_ICONS, provided by its route (rules.routes.ts). */
export const RULES_ICONS = {
  phosphorArchive,
  phosphorArrowCounterClockwise,
  phosphorArrowDown,
  phosphorArrowSquareOut,
  phosphorArrowUp,
  phosphorCircleDashed,
  phosphorEquals,
  phosphorFloppyDisk,
  phosphorListChecksDuotone,
  phosphorMinusCircle,
  phosphorNotePencil,
  phosphorPencilSimple,
  phosphorPlus,
  phosphorPlusCircle,
  phosphorScalesDuotone,
  phosphorSealCheck,
  phosphorSealCheckDuotone,
  phosphorSpinner,
  phosphorSpinnerDuotone,
  phosphorTrash,
  phosphorWarningCircle,
};

/** While an extraction runs the list is fetched again this often (and never while the tab is hidden). */
const POLL_MS = 5000;

/** `index` is the option's place in the whole list, for the entrance stagger. */
interface VersionOption { id: string; name: string; row: RuleVersionRow; index: number }
interface VersionGroup { label: string; code: string; name: string; items: VersionOption[] }

@Component({
  selector: 'lx-rules',
  imports: [FormsModule, NgIcon, ButtonDirective, ButtonLabel, ConfirmDialog, Listbox, Message, Skeleton, Enter, InfoTip, NewRuleVersionDialog, RuleVersionDetail],
  providers: [UnsavedChanges, ConfirmationService],
  templateUrl: './rules.html',
  styleUrl: './rules.css',
  host: { '(window:beforeunload)': 'onBeforeUnload($event)' },
})
export class Rules {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly unsaved = inject(UnsavedChanges);
  readonly session = inject(SessionService);
  readonly context = inject(ProgramContext);

  readonly programId = input.required<string>();
  /** From the route "rules/:ruleVersionId"; absent on "rules". */
  readonly ruleVersionId = input<string>();
  /** `?new=1` (the command palette's New rule version) opens the new-version dialog for those who may enter one. */
  readonly new = input<string>();

  readonly users = signal<UserSummary[]>([]);
  readonly error = signal<string | null>(null);
  /** The polite live region (data-testid="message"): the last result in words. */
  readonly message = signal<string | null>(null);
  readonly newOpen = signal(false);
  private readonly pageVisible = signal(typeof document === 'undefined' || document.visibilityState === 'visible');
  private readonly newButton = viewChild<ElementRef<HTMLButtonElement>>('newButton');

  readonly groups = computed<VersionGroup[]>(() => {
    let index = 0;
    return this.context.ruleVersionGroups().map((g) => ({
      code: g.code,
      name: g.name,
      label: `${g.name} (${g.code})`,
      items: g.rows.map((row) => ({
        id: row.view.id,
        name: `${row.view.jurisdiction}, ${row.view.label}, effective ${row.view.effectiveFrom}, ${RULE_STATUS[row.status].label}`,
        row,
        index: index++,
      })),
    }));
  });
  readonly versionCount = computed(() => this.context.ruleVersions().length);
  /** The list's value: follows the route, and snaps back when a guarded navigation is cancelled. */
  readonly listValue = linkedSignal<string | null>(() => this.ruleVersionId() ?? null);
  readonly selected = computed(() => this.context.ruleVersions().find((v) => v.id === this.ruleVersionId()) ?? null);

  constructor() {
    registerComponentTokens(RULES_TOKENS);
    effect(() => {
      const id = this.programId();
      untracked(() => this.context.ensure(id));
    });
    effect(() => {
      if (this.new() === '1' && this.session.canEnterRules()) {
        untracked(() => {
          this.newOpen.set(true);
          this.router.navigate([], { queryParams: { new: null }, queryParamsHandling: 'merge', replaceUrl: true });
        });
      }
    });
    firstValueFrom(this.api.users()).then((users) => this.users.set(users), () => this.users.set([]));

    // Poll only while some extraction is still running and the page is visible; each answer re-arms the timer.
    effect((onCleanup) => {
      if (!this.pageVisible() || !anyExtracting(this.context.ruleVersions())) { return; }
      const timer = setInterval(() => this.refresh(), POLL_MS);
      onCleanup(() => clearInterval(timer));
    });
    const onVisibility = (): void => this.pageVisible.set(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', onVisibility);
    inject(DestroyRef).onDestroy(() => document.removeEventListener('visibilitychange', onVisibility));
  }

  statusOf(row: RuleVersionRow): (typeof RULE_STATUS)[RuleVersionRow['status']] {
    return RULE_STATUS[row.status];
  }

  async open(id: string | null): Promise<void> {
    if (!id || id === this.ruleVersionId()) { return; }
    this.listValue.set(id);
    const moved = await this.router.navigate(['/programs', this.programId(), 'rules', id]);
    if (!moved) { this.listValue.set(this.ruleVersionId() ?? null); }
  }

  /** The route's CanDeactivate guard: unsaved list edits are discarded only when the person says so. */
  confirmLeave(nextUrl: string): boolean | Promise<boolean> {
    if (!leaveNeedsConfirmation(this.unsaved.dirty(), this.router.url, nextUrl)) { return true; }
    return new Promise((resolve) => this.confirmation.confirm({
      header: 'Discard unsaved changes to this list?',
      message: 'The requirement list has edits that are not saved. Discarding them restores the list as it was last saved.',
      acceptLabel: 'Discard changes',
      rejectLabel: 'Keep editing',
      acceptButtonProps: { severity: 'danger', outlined: true },
      rejectButtonProps: { severity: 'primary' },
      defaultFocus: 'reject',
      closeOnEscape: true,
      accept: () => resolve(true),
      reject: () => resolve(false),
    }));
  }

  /** A reload or closing the tab with unsaved edits gets the browser's own question. */
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.unsaved.dirty()) { event.preventDefault(); }
  }

  /** A version was entered: show it, already extracting, and say so. */
  async onCreated(id: string): Promise<void> {
    await this.refresh();
    this.open(id);
    this.notify({ summary: 'Extraction started', detail: 'The model is proposing the requirement list. It appears here for review when it is ready.' });
  }

  onNewClosed(): void {
    this.newButton()?.nativeElement.focus();
  }

  notify(notice: Notice): void {
    this.message.set(`${notice.summary}. ${notice.detail}`);
    this.toast.add({ severity: notice.severity ?? 'success', summary: notice.summary, detail: notice.detail });
  }

  async refresh(): Promise<void> {
    try {
      await this.context.refreshRuleVersions();
      this.error.set(null);
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The rule versions could not be loaded.'));
    }
  }
}
