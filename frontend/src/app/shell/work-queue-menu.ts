import { Component, computed, inject, output, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  phosphorGitDiff, phosphorListChecks, phosphorScales, phosphorSignature, phosphorTray, phosphorWarning, phosphorWarningCircle,
} from '@ng-icons/phosphor-icons/regular';
import { phosphorTrayDuotone } from '@ng-icons/phosphor-icons/duotone';
import { Popover } from 'primeng/popover';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../core/api.service';
import { WorkItem } from '../core/models';
import { problemDetail } from '../core/problem';
import { ProgramContext } from '../core/program-context';
import { WORK_QUEUE_LIMIT, workItemView } from '../core/work-queue';

export const WORK_QUEUE_ICONS = {
  phosphorGitDiff, phosphorListChecks, phosphorScales, phosphorSignature, phosphorTray, phosphorWarning, phosphorWarningCircle, phosphorTrayDuotone,
};

/**
 * The work queue popover (F13, DESIGN.md "Shell"): what needs the signed-in person, at most eight items, each a link
 * to its screen. The list is fetched each time it opens; `total` reports the full count back to the shell's badge.
 */
@Component({
  selector: 'lx-work-queue-menu',
  imports: [RouterLink, NgIcon, Popover],
  template: `
    <p-popover #panel ariaLabel="Work queue" styleClass="lx-queue" (onShow)="opened.emit(true)" (onHide)="opened.emit(false)">
      <div class="lx-queue-body">
        <h2 class="lx-queue-title">Needs you</h2>
        @if (error(); as e) {
          <p class="lx-queue-note" data-testid="work-queue-error">{{ e }}</p>
        } @else if (items(); as list) {
          @if (list.length === 0) {
            <div class="lx-queue-empty">
              <ng-icon name="phosphorTrayDuotone" size="32" aria-hidden="true" />
              <p>Nothing needs you.</p>
            </div>
          } @else {
            <ul class="lx-queue-list">
              @for (item of list; track $index) {
                <li>
                  <a class="lx-queue-item" [routerLink]="item.commands" [queryParams]="item.queryParams" (click)="panel.hide()">
                    <span class="lx-queue-icon"><ng-icon [name]="item.icon" size="18" aria-hidden="true" /></span>
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
    .lx-queue-body { width: min(22rem, calc(100vw - 48px)); }
    .lx-queue-title { padding: 0 4px 8px; }
    .lx-queue-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
    .lx-queue-item {
      display: flex; align-items: center; gap: 12px; padding: 8px; border-radius: 8px; color: var(--p-lx-text); text-decoration: none;
      transition: background-color var(--lx-t-1) var(--lx-ease-out);
    }
    .lx-queue-item:hover { background: var(--p-lx-surface-2); color: var(--p-lx-text); }
    .lx-queue-item:focus-visible { outline-offset: 0; }
    .lx-queue-icon { flex: none; width: 32px; height: 32px; border-radius: 8px; display: grid; place-items: center; background: var(--p-lx-accent-tint); color: var(--p-lx-accent); }
    .lx-queue-text { display: flex; flex-direction: column; min-width: 0; }
    .lx-queue-item-title { font-weight: 550; }
    .lx-queue-detail { font-size: 13px; line-height: 20px; color: var(--p-lx-text-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .lx-queue-empty { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 16px 0 8px; color: var(--p-lx-text-2); }
    .lx-queue-empty ng-icon { color: var(--p-lx-accent); }
    .lx-queue-note { padding: 8px 4px; color: var(--p-lx-text-2); }
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
  /** The total behind the badge, from the list just fetched. */
  readonly total = output<number>();

  readonly items = computed(() => {
    const queue = this.queue();
    const programId = this.context.program()?.id ?? null;
    return queue?.slice(0, WORK_QUEUE_LIMIT).map((item) => workItemView(item, programId, (code) => this.context.jurisdictionName(code))) ?? null;
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
