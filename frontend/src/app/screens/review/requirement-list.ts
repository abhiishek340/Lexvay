import { Component, ElementRef, afterRenderEffect, input, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon } from '@ng-icons/core';
import { SelectButton } from 'primeng/selectbutton';
import { Skeleton } from 'primeng/skeleton';
import { Tooltip } from 'primeng/tooltip';
import { Enter } from '../../ui/enter';
import { StatusIcon } from '../../ui/status-icon';
import { ReviewFilter, ReviewWorkspace } from './review-workspace';

/**
 * The 288 px list rail of Review: the filter as a segmented icon control (counts in the segment names and tooltips),
 * then one row per requirement with its ref, title and one StatusIcon. The row whose decision just landed (`landed`)
 * draws an ink ring around its new icon and settles on the spring; the selected row stays in view as J and K move.
 */
@Component({
  selector: 'lx-requirement-list',
  imports: [FormsModule, NgIcon, SelectButton, Skeleton, Tooltip, Enter, StatusIcon],
  templateUrl: './requirement-list.html',
  styleUrl: './requirement-list.css',
})
export class RequirementList {
  readonly workspace = input.required<ReviewWorkspace>();
  readonly loading = input(false);
  /** The mapping whose decision has just been recorded, for the landing motion; null once it has played. */
  readonly landed = input<string | null>(null);
  readonly selectRequirement = output<string>();
  readonly filterChange = output<ReviewFilter>();
  readonly landingDone = output<void>();

  private readonly body = viewChild<ElementRef<HTMLElement>>('body');

  constructor() {
    // Keeps the selected row visible inside the list only; the page itself never moves (under 1024 px the page scrolls).
    afterRenderEffect(() => {
      this.workspace().selectedId();
      const body = this.body()?.nativeElement;
      const row = body?.querySelector('[aria-current="true"]');
      if (!body || !row) { return; }
      const outer = body.getBoundingClientRect();
      const inner = row.getBoundingClientRect();
      if (inner.top < outer.top) {
        body.scrollTop -= outer.top - inner.top;
      } else if (inner.bottom > outer.bottom) {
        body.scrollTop += inner.bottom - outer.bottom;
      }
    });
  }

  setFilter(filter: ReviewFilter | null): void {
    if (filter) {
      this.filterChange.emit(filter);
    }
  }
}
