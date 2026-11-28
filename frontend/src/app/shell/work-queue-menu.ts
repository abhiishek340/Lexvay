import { Component, computed, inject, output, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { phosphorSignature, phosphorWarning, phosphorWarningCircle } from '@ng-icons/phosphor-icons/regular';
import { Popover } from 'primeng/popover';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../core/api.service';
import { WorkItem, WorkQueueKind } from '../core/models';
import { problemDetail } from '../core/problem';
import { ProgramContext } from '../core/program-context';
import { WORK_QUEUE_LIMIT, workItemView } from '../core/work-queue';
import type { IconName } from '../theme/icons';

/** The kinds' icons the shell does not already register (SHELL_ICONS has the rail's). */
export const WORK_QUEUE_ICONS = { phosphorSignature, phosphorWarning, phosphorWarningCircle };

/** Each kind's icon: the screen's own rail icon where it has one. */
const KIND_ICON: Record<WorkQueueKind, IconName> = {
  REQUIREMENTS_TO_REVIEW: 'phosphorListChecks',
  EVIDENCE_REQUESTS_OPEN: 'phosphorTray',
  RULE_VERSIONS_TO_APPROVE: 'phosphorScales',
  EXTRACTION_FAILED: 'phosphorWarningCircle',
  CHANGES_OPEN: 'phosphorGitDiff',
  READY_TO_SIGN: 'phosphorSignature',
  ASSESSMENTS_FAILED: 'phosphorWarning',
};

/**
 * The work queue popover (F13, DESIGN.md "Shell"): what needs the signed-in person, at most eight items, each a 16 px
 * icon in ink-2, its count-first title and where it is, linking to its screen. The list is fetched each time it opens;
 * `total` reports the full count back to the shell, which shows a dot while anything waits.
 */
@Component({
  selector: 'lx-work-queue-menu',
  imports: [RouterLink, NgIcon, Popover],
  template: `
    <p-popover #panel ariaLabel="Work queue" (onShow)="opened.emit(true)" (onHide)="opened.emit(false)">
      <div class="lx-queue-body">
        <h2 class="lx-queue-title">Needs you</h2>
        @if (error(); as e) {
          <p class="lx-queue-note lx-missing" data-testid="work-queue-error">{{ e }}</p>
        } @else if (items(); as list) {
          @if (list.length === 0) {
            <p class="lx-queue-note">Nothing needs you.</p>
          } @else {
            <ul class="lx-queue-list">
              @for (item of list; track $index) {
                <li>
                  <a class="lx-queue-item" [routerLink]="item.commands" [queryParams]="item.queryParams" (click)="panel.hide()">
                    <ng-icon [name]="item.icon" size="16" aria-hidden="true" />
                    <span class="lx-queue-text"><span class="lx-queue-item-title">{{ item.title }}</span><span class="lx-queue-detail">{{ item.detail }}</span></span>
                  </a>
                </li>
              }
            </ul>
          }
        } @else {
          <p class="lx-queue-note" aria-busy="true">Loading</p>
        }
      </div>
    </p-popover>
  `,
  styles: `
    .lx-queue-body { width: min(20rem, calc(100vw - 48px)); }
    .lx-queue-title { font-size: 13px; line-height: 20px; padding: 0 0 8px; }
    .lx-queue-list { list-style: none; margin: 0 -8px; padding: 0; display: flex; flex-direction: column; }
    .lx-queue-item {
      display: flex; align-items: flex-start; gap: 12px; padding: 6px 8px; border-radius: 4px; color: var(--p-lx-ink); text-decoration: none;
      transition: background-color var(--lx-t-fast) var(--lx-ease);
    }
    .lx-queue-item:hover { background: var(--p-lx-sunken); color: var(--p-lx-ink); }
    .lx-queue-item:focus-visible { outline-offset: -2px; }
    .lx-queue-item ng-icon { flex: none; margin-top: 2px; color: var(--p-lx-ink-2); }
    .lx-queue-text { display: flex; flex-direction: column; min-width: 0; }
    .lx-queue-item-title { font-size: 13px; line-height: 20px; font-weight: 500; }
    .lx-queue-detail { font-size: 12px; line-height: 16px; color: var(--p-lx-ink-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .lx-queue-note { font-size: 13px; line-height: 20px; color: var(--p-lx-ink-2); }
  `,
  viewProviders: [provideIcons(WORK_QUEUE_ICONS)],
})
export class WorkQueueMenu {
  private readonly api = inject(ApiService);
  private readonly context = inject(ProgramContext);
  private readonly panel = viewChild.required<Popover>('panel');
  private readonly queue = signal<WorkItem[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly opened = output<boolean>();
  /** The total behind the shell's dot, from the list just fetched. */
  readonly total = output<number>();

  readonly items = computed(() => {
    const queue = this.queue();
    const programId = this.context.program()?.id ?? null;
    return queue?.slice(0, WORK_QUEUE_LIMIT)
      .map((item) => ({ ...workItemView(item, programId, (code) => this.context.jurisdictionName(code)), icon: KIND_ICON[item.kind] })) ?? null;
  });

  async toggle(event: Event): Promise<void> {
    const opening = !this.panel().overlayVisible();
    this.panel().toggle(event);
    if (!opening) { return; }
    this.error.set(null);
    try {
      const queue = await firstValueFrom(this.api.workQueue());
      this.queue.set(queue.items);
      this.total.emit(queue.items.reduce((sum, item) => sum + item.count, 0));
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The work queue could not be loaded.'));
    }
  }
}
