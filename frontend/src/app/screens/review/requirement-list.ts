import { Component, ElementRef, afterRenderEffect, input, output, viewChild } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { Skeleton } from 'primeng/skeleton';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { StatusMark } from '../../ui/status-mark';
import { requirementStatus } from '../../ui/status';
import { REVIEW_FILTERS, ReviewFilter, ReviewWorkspace } from './review-workspace';

/**
 * The 280 px requirement list of Review: the filter as a text tab row with counts (All, To review, Found, Ambiguous,
 * Missing; it wraps inside the column), then one row per requirement with its ref, title and one status mark whose
 * words are for assistive technology. The selected row stays in view as J and K move.
 */
@Component({
  selector: 'lx-requirement-list',
  imports: [NgIcon, Skeleton, Tab, TabList, TabPanel, TabPanels, Tabs, StatusMark],
  templateUrl: './requirement-list.html',
  styleUrl: './requirement-list.css',
})
export class RequirementList {
  readonly workspace = input.required<ReviewWorkspace>();
  readonly loading = input(false);
  readonly selectRequirement = output<string>();
  readonly filterChange = output<ReviewFilter>();
  readonly status = requirementStatus;

  private readonly body = viewChild('body', { read: ElementRef<HTMLElement> });

  constructor() {
    // Keeps the selected row visible inside the list only; the page itself never moves (under 1024 px the page scrolls).
    afterRenderEffect(() => {
      this.workspace().selectedId();
      const body = this.body()?.nativeElement as HTMLElement | undefined;
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

  setFilter(value: string | number | undefined): void {
    const filter = REVIEW_FILTERS.find((f) => f.value === value)?.value;
    if (filter) {
      this.filterChange.emit(filter);
    }
  }
}
