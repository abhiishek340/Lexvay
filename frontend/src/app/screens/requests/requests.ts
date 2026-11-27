import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon } from '@ng-icons/core';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { SelectButton } from 'primeng/selectbutton';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { REQUEST_FILTERS, REQUEST_STATE, RequestFilter, canAnswer, matchesRequestFilter, requestCounts, requestState } from '../../core/evidence-requests';
import { EvidenceRequest, EvidenceResponse } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { EvidenceDrawer } from './evidence-drawer';

/**
 * The evidence request inbox (F4): what reviewers asked the program for, and the drawer in which anyone signed in
 * answers with a passage of a program document. The rows are ProgramContext's, so the rail badge and the table
 * never disagree.
 */
@Component({
  selector: 'lx-requests',
  imports: [DatePipe, FormsModule, NgIcon, ButtonDirective, ButtonLabel, Message, SelectButton, Skeleton, TableModule, Tag, EvidenceDrawer],
  templateUrl: './requests.html',
  styleUrl: './requests.css',
})
export class Requests {
  private readonly toast = inject(MessageService);
  readonly context = inject(ProgramContext);

  readonly programId = input.required<string>();
  readonly filters = REQUEST_FILTERS;
  readonly canAnswer = canAnswer;

  readonly filter = signal<RequestFilter>('OPEN');
  readonly rows = computed(() => this.context.evidenceRequests().filter((r) => matchesRequestFilter(r, this.filter())));
  readonly counts = computed(() => requestCounts(this.context.evidenceRequests()));
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

  blocked(row: EvidenceRequest): boolean {
    return requestState(row) === 'BLOCKED';
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

function headingOf(doc: Document): HTMLElement | null {
  const heading = doc.querySelector<HTMLElement>('main h1');
  heading?.setAttribute('tabindex', '-1');
  return heading;
}
