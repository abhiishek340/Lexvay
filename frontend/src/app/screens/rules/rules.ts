import { Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Listbox } from 'primeng/listbox';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { Tag } from 'primeng/tag';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { UserSummary } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { RULE_STATUS, RuleVersionRow, anyExtracting, groupRuleVersions } from '../../core/rules';
import { SessionService } from '../../core/session.service';
import { NewRuleVersionDialog } from './new-rule-version';
import { Notice, RuleVersionDetail } from './rule-version-detail';

/** While an extraction runs the list is fetched again this often (and never while the tab is hidden). */
const POLL_MS = 5000;

interface VersionOption { id: string; name: string; row: RuleVersionRow }
interface VersionGroup { label: string; code: string; items: VersionOption[] }

@Component({
  selector: 'lx-rules',
  imports: [FormsModule, NgIcon, ButtonDirective, ButtonLabel, Listbox, Message, Skeleton, Tag, NewRuleVersionDialog, RuleVersionDetail],
  templateUrl: './rules.html',
  styleUrl: './rules.css',
})
export class Rules {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);
  readonly session = inject(SessionService);
  readonly context = inject(ProgramContext);

  readonly programId = input.required<string>();
  /** From the route "rules/:ruleVersionId"; absent on "rules". */
  readonly ruleVersionId = input<string>();

  readonly users = signal<UserSummary[]>([]);
  readonly error = signal<string | null>(null);
  /** The polite live region (data-testid="message"): the last result in words. */
  readonly message = signal<string | null>(null);
  readonly newOpen = signal(false);
  private readonly pageVisible = signal(typeof document === 'undefined' || document.visibilityState === 'visible');
  private readonly newButton = viewChild<ElementRef<HTMLButtonElement>>('newButton');

  readonly groups = computed<VersionGroup[]>(() => {
    const program = this.context.program();
    return groupRuleVersions(this.context.ruleVersions(), program?.jurisdictions ?? [], (code) => this.context.jurisdictionName(code)).map((g) => ({
      code: g.code,
      label: `${g.name} (${g.code})`,
      items: g.rows.map((row) => ({
        id: row.view.id,
        name: `${row.view.jurisdiction}, ${row.view.label}, effective ${row.view.effectiveFrom}, ${RULE_STATUS[row.status].label}`,
        row,
      })),
    }));
  });
  readonly selected = computed(() => this.context.ruleVersions().find((v) => v.id === this.ruleVersionId()) ?? null);

  constructor() {
    effect(() => {
      const id = this.programId();
      untracked(() => this.context.ensure(id));
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

  open(id: string | null): void {
    if (id && id !== this.ruleVersionId()) {
      this.router.navigate(['/programs', this.programId(), 'rules', id]);
    }
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
