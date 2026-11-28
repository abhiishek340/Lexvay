import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Accordion, AccordionContent, AccordionHeader, AccordionPanel } from 'primeng/accordion';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { REQUIREMENT_TYPE_LABEL, numericCheck } from '../../core/labels';
import { DiffRow, DiffSegment, DiffStatus, RuleVersionDiff, RuleVersionView } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { comparableVersions, effectiveText } from '../../core/rules';
import { DiffText } from '../../ui/diff-text';
import { DIFF_STATUS } from './diff-status';
import { TermsDiff, termsDiff } from './search-terms';
import { Label } from '../../ui/label';

/** Whether the unchanged requirements are shown: remembered for the session, across versions and visits to Compare. */
const showUnchanged = signal(false);

const SUMMARY_ORDER: { status: DiffStatus; key: keyof RuleVersionDiff['summary'] }[] = [
  { status: 'CHANGED', key: 'changed' }, { status: 'ADDED', key: 'added' }, { status: 'REMOVED', key: 'removed' }, { status: 'UNCHANGED', key: 'unchanged' },
];

/** The Compare tab: this version against the one it supersedes, or another approved version of the same rule. */
@Component({
  selector: 'lx-rule-compare',
  imports: [FormsModule, NgTemplateOutlet, Accordion, AccordionContent, AccordionHeader, AccordionPanel, Message, Select, Skeleton, Label, DiffText],
  templateUrl: './rule-compare.html',
  styleUrl: './rule-compare.css',
})
export class RuleCompare {
  private readonly api = inject(ApiService);
  readonly version = input.required<RuleVersionView>();
  readonly all = input.required<readonly RuleVersionView[]>();
  readonly jurisdictionName = input.required<string>();

  readonly against = signal<string | null>(null);
  readonly diff = signal<RuleVersionDiff | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly options = computed(() => {
    const v = this.version();
    return comparableVersions(this.all(), v.jurisdiction, v.licenseType, v.id).map((o) => ({ id: o.id, label: `${o.label} (effective ${effectiveText(o)})` }));
  });
  /** Changed rows first, then added, removed and unchanged; the server's list order within each. */
  /** What changed, first: changed rows, then added and removed; the server's list order within each. */
  readonly changedRows = computed(() => {
    const d = this.diff();
    return d ? SUMMARY_ORDER.filter(({ status }) => status !== 'UNCHANGED').flatMap(({ status }) => d.requirements.filter((r) => r.status === status)) : [];
  });
  /** The unchanged ones, behind one disclosure under the changes. */
  readonly unchangedRows = computed(() => this.diff()?.requirements.filter((r) => r.status === 'UNCHANGED') ?? []);
  readonly showUnchanged = showUnchanged;
  readonly effective = effectiveText;
  readonly summary = computed(() => {
    const d = this.diff();
    return d ? SUMMARY_ORDER.map(({ status, key }) => ({ status, count: d.summary[key], ...DIFF_STATUS[status] })) : [];
  });

  constructor() {
    // A newly shown version starts from the version it supersedes.
    effect(() => {
      const v = this.version();
      untracked(() => this.choose(v.supersedesId));
    });
  }

  async choose(against: string | null): Promise<void> {
    const v = this.version();
    this.against.set(against);
    this.diff.set(null);
    this.error.set(null);
    if (!against) { return; }
    this.loading.set(true);
    try {
      const diff = await firstValueFrom(this.api.ruleVersionDiff(v.id, against));
      if (this.version().id === v.id && this.against() === against) { this.diff.set(diff); }
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The comparison could not be loaded.'));
    } finally {
      this.loading.set(false);
    }
  }

  statusOf(row: DiffRow): (typeof DIFF_STATUS)[DiffStatus] {
    return DIFF_STATUS[row.status];
  }

  ref(row: DiffRow): string {
    return (row.after ?? row.before)?.ref ?? '';
  }

  title(row: DiffRow): string {
    return (row.after ?? row.before)?.title ?? '';
  }

  /** An added requirement's text as inserted, a removed one's as deleted, an unchanged one's as it is. */
  whole(row: DiffRow): DiffSegment[] {
    const op = row.status === 'ADDED' ? 'INSERT' : row.status === 'REMOVED' ? 'DELETE' : 'EQUAL';
    return [{ op, text: (row.after ?? row.before)?.text ?? '' }];
  }

  /** The old and the new value of one aspect, marked as deleted and inserted. */
  replaced(before: string | null | undefined, after: string | null | undefined): { before: DiffSegment[]; after: DiffSegment[] } {
    return { before: [{ op: 'DELETE', text: before ?? '' }], after: [{ op: 'INSERT', text: after ?? '' }] };
  }

  check(row: DiffRow, side: 'before' | 'after'): string {
    const r = row[side];
    return (r && numericCheck(r)) ?? 'no number';
  }

  /** The search terms of both sides, with the removed and the added ones marked (a terms-only change is its own aspect, D-254). */
  terms(row: DiffRow): TermsDiff {
    return termsDiff(row.before?.searchTerms ?? [], row.after?.searchTerms ?? []);
  }

  typeOf(row: DiffRow, side: 'before' | 'after'): string {
    const r = row[side];
    return r ? REQUIREMENT_TYPE_LABEL[r.type] : '';
  }
}
