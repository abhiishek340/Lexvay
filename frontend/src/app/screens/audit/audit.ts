import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon } from '@ng-icons/core';
import { phosphorCopy } from '@ng-icons/phosphor-icons/regular';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuditEntry, UserSummary } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { groupByDay } from '../../ui/days';
import { Help, HelpTerm } from '../../ui/help';
import { SkeletonRows } from '../../ui/skeleton-rows';
import { AUDIT_TOKENS } from './audit-theme';
import {
  ACTION_OPTIONS, AuditQuery, NO_QUERY, PERIOD_OPTIONS, RECORD_TYPE_OPTIONS, VerificationView, auditCsvUrl, auditParams, auditRows, personOptions,
  verificationView,
} from './audit-view';

/** The icons the Audit trail shows beyond SHELL_ICONS, provided by its route (audit.routes.ts). */
export const AUDIT_ICONS = { phosphorCopy };

const HELP: readonly HelpTerm[] = [
  { term: 'Audit trail', definition: 'Every action, newest first: who did it, when, what it was and on which record.' },
  { term: 'Append-only', definition: 'Entries are only ever added, never changed or removed.' },
  { term: 'Verify', definition: 'Recomputes the chain of seals over the entries. Pending entries are written but not sealed yet. The head hash is the newest seal.' },
];

/** Entries per request; "Show older" asks for the next page with the same filters. */
const PAGE = 100;

/**
 * The audit trail (DESIGN.md "Audit trail"): the entries the server returns for the filters on screen, newest first,
 * as a log grouped by day, each entry one sentence with its time that opens below itself to its worded details. An
 * administrator can verify the chain of seals (SEC-3): the result is one sentence under the title, with the head hash.
 */
@Component({
  selector: 'lx-audit',
  imports: [DatePipe, FormsModule, NgIcon, ButtonDirective, ButtonLabel, Message, Select, Help, SkeletonRows],
  templateUrl: './audit.html',
  styleUrl: './audit.css',
})
export class Audit {
  readonly help = HELP;
  private readonly api = inject(ApiService);
  private readonly toast = inject(MessageService);
  readonly session = inject(SessionService);
  private readonly context = inject(ProgramContext);
  readonly programId = input.required<string>();

  readonly actions = ACTION_OPTIONS;
  readonly recordTypes = RECORD_TYPE_OPTIONS;
  readonly periods = PERIOD_OPTIONS;

  readonly query = signal<AuditQuery>(NO_QUERY);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  /** Another page may follow: the last one came back full. */
  readonly more = signal(false);
  private readonly users = signal<UserSummary[] | null>(null);
  /** The pages loaded for the current filters, oldest request first. */
  private readonly pages = signal<AuditEntry[][] | null>(null);
  private readonly loadedAt = signal(new Date());
  private readonly expanded = signal<ReadonlySet<string>>(new Set());
  /** The last verification, while one runs, and why one failed. */
  readonly verification = signal<VerificationView | null>(null);
  readonly verifying = signal(false);
  readonly verifyError = signal<string | null>(null);
  /** Answers to an earlier filter that arrive late are dropped. */
  private request = 0;

  readonly people = computed(() => personOptions(this.users() ?? []));
  readonly rows = computed(() => {
    const pages = this.pages();
    const users = this.users();
    if (!pages || !users) { return null; }
    const names = (code: string): string => this.context.jurisdictionName(code);
    return pages.flatMap((page) => auditRows(page, users, names));
  });
  readonly days = computed(() => groupByDay(this.rows() ?? [], (row) => row.at, this.loadedAt()));
  readonly filtered = computed(() => Object.values(this.query()).some((v) => v !== null));
  readonly csvUrl = computed(() => auditCsvUrl(this.query(), new Date()));
  readonly summary = computed(() => {
    const n = this.rows()?.length;
    if (n === undefined) { return ''; }
    return `${this.more() ? 'Newest ' : ''}${n} ${n === 1 ? 'entry' : 'entries'}`;
  });

  constructor() {
    registerComponentTokens(AUDIT_TOKENS);
    effect(() => {
      const id = this.programId();
      untracked(() => this.context.ensure(id));
    });
    firstValueFrom(this.api.users())
      .then((users) => this.users.set(users))
      .catch((e: unknown) => this.error.set(problemDetail(e, 'The audit trail could not be loaded.')));
    effect(() => {
      const query = this.query();
      untracked(() => this.load(query, 0));
    });
  }

  set<K extends keyof AuditQuery>(key: K, value: AuditQuery[K] | undefined): void {
    this.query.update((q) => ({ ...q, [key]: value ?? null }));
  }

  clear(): void {
    this.query.set(NO_QUERY);
  }

  loadMore(): void {
    this.load(this.query(), this.rows()?.length ?? 0);
  }

  isOpen(id: string): boolean {
    return this.expanded().has(id);
  }

  toggle(id: string): void {
    this.expanded.update((open) => {
      const next = new Set(open);
      if (!next.delete(id)) { next.add(id); }
      return next;
    });
  }

  /** Asks the server to walk the chain of seals (administrators; the check is itself audited). */
  async verify(): Promise<void> {
    if (this.verifying()) { return; }
    this.verifying.set(true);
    this.verifyError.set(null);
    try {
      this.verification.set(verificationView(await firstValueFrom(this.api.verifyAudit())));
    } catch (e: unknown) {
      this.verification.set(null);
      this.verifyError.set(problemDetail(e, 'The audit trail could not be verified.'));
    } finally {
      this.verifying.set(false);
    }
  }

  async copy(hash: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(hash);
      this.toast.add({ severity: 'success', summary: 'Copied', detail: 'The full head hash is on the clipboard.' });
    } catch {
      this.toast.add({ severity: 'error', summary: 'The head hash could not be copied', detail: 'Verify again and select it instead.' });
    }
  }

  private async load(query: AuditQuery, offset: number): Promise<void> {
    const request = ++this.request;
    this.loading.set(true);
    try {
      const now = new Date();
      const page = await firstValueFrom(this.api.audit(auditParams(query, now), PAGE, offset));
      if (request !== this.request) { return; }
      if (offset === 0) {
        this.pages.set([page]);
        this.loadedAt.set(now);
        this.expanded.set(new Set());
      } else {
        this.pages.update((pages) => [...(pages ?? []), page]);
      }
      this.more.set(page.length === PAGE);
      this.error.set(null);
    } catch (e: unknown) {
      if (request === this.request) { this.error.set(problemDetail(e, 'The audit trail could not be loaded.')); }
    } finally {
      if (request === this.request) { this.loading.set(false); }
    }
  }
}
