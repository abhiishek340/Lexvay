import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { phosphorCaretRight } from '@ng-icons/phosphor-icons/regular';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { SelectButton } from 'primeng/selectbutton';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Tooltip } from 'primeng/tooltip';
import { BlockedReason, REQUEST_FILTERS, RequestFilter, RequestState, answersText, blockedReason, canAnswer, matchesRequestFilter, requestCounts, requestState } from '../../core/evidence-requests';
import { EvidenceRequest, EvidenceResponse } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { registerComponentTokens } from '../../theme/component-tokens';
import { EvidenceDrawer } from './evidence-drawer';
import { REQUESTS_TOKENS } from './requests-theme';
import { Status, StatusMark } from '../../ui/status-mark';
import { Help, HelpTerm } from '../../ui/help';

/** The inbox's definitions, behind its one Help button. */
const HELP: readonly HelpTerm[] = [
  { term: 'Evidence request', definition: 'A reviewer asks the program for evidence from the Review screen.' },
  { term: 'Answer', definition: "A passage from one of the program's documents; the requirement goes back to the reviewer beside what the system proposed." },
  { term: 'Blocked', definition: 'A change or a signed determination closed the request; the row says which.' },
];

/** The icons the inbox and its answer drawer show beyond SHELL_ICONS, provided by its route (requests.routes.ts). */
export const REQUESTS_ICONS = { phosphorCaretRight };

/**
 * Each state as a mark and its own word (ui/status-mark.ts): an open request is evidence requested (ring with a centre
 * dot), an answered one waits for the reviewer (not reviewed, an outline ring), a blocked one is blocked (outline square).
 */
export const REQUEST_STATE: Record<RequestState, { key: RequestState; label: string; mark: Status }> = {
  OPEN: { key: 'OPEN', label: 'Open', mark: 'EVIDENCE_REQUESTED' },
  ANSWERED: { key: 'ANSWERED', label: 'Answered', mark: 'NOT_REVIEWED' },
  BLOCKED: { key: 'BLOCKED', label: 'Blocked', mark: 'BLOCKED' },
};

/** Why a blocked request has no answer action: a few words in the row, the sentence in their tooltip. */
export const BLOCKED_TEXT: Record<BlockedReason, { short: string; why: string }> = {
  STALE: { short: 'Waiting for a re-run', why: 'This requirement is waiting for a re-run after a change; answer it after the re-run.' },
  SIGNED: { short: 'Signed', why: 'A signed determination already covers this requirement, so the request can no longer be answered.' },
};

/**
 * The evidence request inbox (F4): what reviewers asked the program for, and the drawer in which anyone signed in
 * answers with a passage of a program document. The rows are ProgramContext's, so the rail badge and the table
 * never disagree.
 */
@Component({
  selector: 'lx-requests',
  imports: [DatePipe, FormsModule, RouterLink, NgIcon, ButtonDirective, ButtonLabel, Message, SelectButton, Skeleton, TableModule, Tooltip, Help, StatusMark, EvidenceDrawer],
  templateUrl: './requests.html',
  styleUrl: './requests.css',
})
export class Requests {
  readonly help = HELP;
  private readonly toast = inject(MessageService);
  readonly context = inject(ProgramContext);

  readonly programId = input.required<string>();
  readonly filters = REQUEST_FILTERS;
  readonly canAnswer = canAnswer;
  readonly answersText = answersText;
  readonly blockedText = BLOCKED_TEXT;

  readonly filter = signal<RequestFilter>('OPEN');
  readonly rows = computed(() => this.context.evidenceRequests().filter((r) => matchesRequestFilter(r, this.filter())));
  readonly counts = computed(() => requestCounts(this.context.evidenceRequests()));
  /** The title's meta line: how many requests there are, and how many of them are blocked. */
  readonly meta = computed(() => {
    const all = this.context.evidenceRequests();
    const blocked = all.filter((r) => requestState(r) === 'BLOCKED').length;
    return `${all.length} ${all.length === 1 ? 'request' : 'requests'}${blocked ? ` · ${blocked} blocked` : ''}`;
  });
  readonly emptyText = computed(() => EMPTY[this.filter()]);
  /** PrimeNG's row expansion state, keyed by mapping id. */
  expanded: Record<string, boolean> = {};
  readonly error = signal<string | null>(null);
  /** The polite live region (data-testid="message"): the last result in words. */
  readonly message = signal<string | null>(null);
  /** The request in the drawer; kept after closing so the content stays while the drawer slides out. */
  readonly answering = signal<EvidenceRequest | null>(null);
  readonly drawerOpen = signal(false);
  private opener: HTMLElement | null = null;

  constructor() {
    registerComponentTokens(REQUESTS_TOKENS);
    // A program another screen already loaded is fetched again here: the requests are other people's work.
    effect(() => {
      const id = this.programId();
      untracked(() => {
        if (this.context.ready() && this.context.program()?.id === id) {
          this.reload();
        } else {
          this.context.ensure(id);
        }
      });
    });
  }

  count(filter: RequestFilter): number {
    return this.counts()[filter];
  }

  state(row: EvidenceRequest) {
    return REQUEST_STATE[requestState(row)];
  }

  blocked(row: EvidenceRequest): BlockedReason | null {
    return blockedReason(row);
  }

  setFilter(filter: RequestFilter | null): void {
    if (filter) { this.filter.set(filter); }
  }

  answer(row: EvidenceRequest, trigger: EventTarget | null): void {
    this.opener = trigger instanceof HTMLElement ? trigger.closest('button') : null;
    this.message.set(null);
    this.answering.set(row);
    this.drawerOpen.set(true);
  }

  /**
   * Closing without an answer returns focus to the row action. After an answer that action no longer exists (the
   * request is answered), so focus goes to the page heading, next to the status line that says what happened.
   */
  closed(answered = false): void {
    this.drawerOpen.set(false);
    const opener = answered ? null : this.opener;
    this.opener = null;
    (opener ?? headingOf(document))?.focus();
  }

  async submitted(row: EvidenceRequest, response: EvidenceResponse): Promise<void> {
    const text = `Evidence for ${row.requirementRef} sent to review: ${response.filename}, page ${response.pageNumber}.`;
    this.message.set(text);
    this.toast.add({ severity: 'success', summary: 'Evidence sent to review', detail: text });
    await this.reload();
    this.closed(true);
  }

  /** A 409: the request changed meanwhile (stale, signed or no longer open); the drawer says why, the list reloads. */
  conflicted(): void {
    this.reload();
  }

  private async reload(): Promise<void> {
    try {
      this.error.set(null);
      await this.context.refreshEvidenceRequests();
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The evidence requests could not be loaded.'));
    }
  }
}

/** Empty states are one sentence (DESIGN.md "States and feedback"). */
const EMPTY: Record<RequestFilter, string> = {
  OPEN: 'No open evidence requests.',
  ANSWERED: 'No answered requests are waiting for review.',
  ALL: 'No evidence requests yet.',
};

function headingOf(doc: Document): HTMLElement | null {
  const heading = doc.querySelector<HTMLElement>('main h1');
  heading?.setAttribute('tabindex', '-1');
  return heading;
}
