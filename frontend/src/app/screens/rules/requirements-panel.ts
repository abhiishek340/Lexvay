import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon } from '@ng-icons/core';
import { AutoComplete } from 'primeng/autocomplete';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { Textarea } from 'primeng/textarea';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { NUMERIC_FIELD_LABEL, NUMERIC_OPERATORS, NUMERIC_TYPES, REQUIREMENT_TYPE_LABEL, numericCheck } from '../../core/labels';
import { NumericField, RequirementType, RequirementView, RequirementsView, RuleVersionView } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { RuleVersionStatus } from '../../core/rules';
import { SessionService } from '../../core/session.service';
import { Clamp } from '../../ui/clamp';
import { Enter } from '../../ui/enter';
import { InfoTip } from '../../ui/info-tip';
import { EditableRequirement, RequirementListEditor } from './requirement-list-editor';
import { MAX_TERMS, applyTerms, droppedTerms, sameTerms } from './search-terms';
import { UnsavedChanges } from './unsaved-changes';
import type { Notice } from './rule-version-detail';

/**
 * The Requirements tab: the version's list as a table, and for a reviewer of an unapproved version the same table
 * with every cell editable in place, saved as a whole list, and the approval that locks it.
 */
@Component({
  selector: 'lx-requirements-panel',
  imports: [FormsModule, NgIcon, AutoComplete, ButtonDirective, ButtonLabel, Dialog, InputNumber, InputText, Message, Select, Skeleton, TableModule, Tag, Textarea, Clamp, Enter, InfoTip],
  templateUrl: './requirements-panel.html',
  styleUrl: './requirements-panel.css',
})
export class RequirementsPanel {
  private readonly api = inject(ApiService);
  readonly session = inject(SessionService);

  readonly version = input.required<RuleVersionView>();
  readonly status = input.required<RuleVersionStatus>();
  readonly jurisdictionName = input.required<string>();
  readonly notice = output<Notice>();
  readonly changed = output<void>();

  readonly editor = new RequirementListEditor();
  readonly check = numericCheck;
  readonly types = (Object.keys(REQUIREMENT_TYPE_LABEL) as RequirementType[]).map((value) => ({ value, label: REQUIREMENT_TYPE_LABEL[value] }));
  readonly fields = (Object.keys(NUMERIC_FIELD_LABEL) as NumericField[]).map((value) => ({ value, label: NUMERIC_FIELD_LABEL[value] }));
  readonly operators = NUMERIC_OPERATORS.map((value) => ({ value, label: value }));
  readonly maxTerms = MAX_TERMS;
  /** Rows keep their DOM (and the focused control) while their content changes. */
  readonly trackRow = (_: number, row: EditableRequirement): number => row.key;

  readonly requirements = signal<RequirementView[] | null>(null);
  readonly approved = signal(false);
  readonly error = signal<string | null>(null);
  readonly editing = signal(false);
  readonly busy = signal(false);
  readonly approveOpen = signal(false);
  /** Per row (by key): why the last entry in its search terms was not taken. */
  readonly termsProblems = signal<ReadonlyMap<number, string>>(new Map());
  private readonly approveButton = viewChild<ElementRef<HTMLButtonElement>>('approveButton');

  /** Correcting and approving belong to reviewers, signers and administrators, and only before approval. */
  readonly canChange = computed(() => this.session.canReview() && !this.approved() && this.requirements() !== null && this.status() !== 'EXTRACTING');
  readonly canApprove = computed(() => this.canChange() && !this.editing() && (this.requirements()?.length ?? 0) > 0);
  readonly rowLabels = computed(() => new Map(this.editor.rows().map((r, i) => [r.key, r.ref.trim() || `Row ${i + 1}`])));
  /** Per row: the search term chips as a named list of items, not PrimeNG's unnamed listbox of options holding controls. */
  readonly termsPt = computed(() => new Map([...this.rowLabels()].map(([key, label]) => [key, {
    inputMultiple: { role: 'list', 'aria-label': `${label} search terms chosen`, 'aria-orientation': null, 'aria-activedescendant': null },
    chipItem: { role: 'listitem', 'aria-selected': null },
    inputChip: { role: 'listitem' },
  }])));

  /** What the list was last loaded for: the version, its requirement count and its approval. */
  private loadedFor = '';

  constructor() {
    effect(() => {
      const v = this.version();
      untracked(() => this.follow(v));
    });
    // The screen asks before navigation or a reload throws these edits away.
    const unsaved = inject(UnsavedChanges);
    effect(() => unsaved.dirty.set(this.editing() && this.editor.dirty()));
  }

  typeName(r: RequirementView): string {
    return REQUIREMENT_TYPE_LABEL[r.type];
  }

  isNumeric(type: RequirementType | null): boolean {
    return type !== null && NUMERIC_TYPES.includes(type);
  }

  startEditing(): void {
    this.editor.load(this.requirements() ?? []);
    this.termsProblems.set(new Map());
    this.error.set(null);
    this.editing.set(true);
  }

  discard(): void {
    this.editor.restore();
    this.termsProblems.set(new Map());
    this.error.set(null);
    this.editing.set(false);
  }

  /** The chips control proposes a new list; the chip model decides what is kept (search-terms.ts). */
  setTerms(row: EditableRequirement, next: string[] | null): void {
    const change = applyTerms(next ?? []);
    // The control echoes the list back once it is reset to the kept terms; that echo changes nothing and keeps the message.
    if (!change.problem && sameTerms(change.terms, row.searchTerms)) { return; }
    // When the model keeps the list as the control holds it, the row keeps that very array: NgModel then has nothing to
    // write back, and the control never resets its input under a person still typing the next term.
    const kept = next && sameTerms(change.terms, next) ? next : change.terms;
    this.editor.update(row.key, { searchTerms: kept });
    this.termsProblems.update((problems) => {
      const updated = new Map(problems);
      if (change.problem) { updated.set(row.key, change.problem); } else { updated.delete(row.key); }
      return updated;
    });
  }

  addRow(): void {
    const row = this.editor.add();
    requestAnimationFrame(() => document.getElementById(this.controlId(row, 'ref'))?.focus());
  }

  controlId(row: EditableRequirement, column: string): string {
    return `req-${row.key}-${column}`;
  }

  /** "CO-3 title": the ref (or the row number while the ref is empty) and the column. */
  cellLabel(row: EditableRequirement, column: string): string {
    return `${this.rowLabels().get(row.key) ?? ''} ${column}`;
  }

  async save(): Promise<void> {
    const v = this.version();
    if (this.busy()) { return; }
    this.busy.set(true);
    this.error.set(null);
    this.editor.clearProblems();
    this.termsProblems.set(new Map());
    try {
      const drafts = this.editor.toDrafts();
      const saved = await firstValueFrom(this.api.replaceRequirements(v.id, drafts));
      this.apply(saved, v);
      this.editing.set(false);
      const dropped = droppedTerms(drafts.map((d) => d.search_terms), saved.requirements.map((r) => r.searchTerms));
      const kept = dropped > 0 ? ` ${dropped} search ${dropped === 1 ? 'term was' : 'terms were'} not kept: ${dropped === 1 ? 'it repeats' : 'they repeat'} the requirement's own words, name a state or contain a number.` : '';
      this.notice.emit({ summary: 'Requirement list saved', detail: `${saved.requirements.length} requirements for ${this.jurisdictionName()} are waiting for approval.${kept}` });
      this.changed.emit();
    } catch (e: unknown) {
      const detail = problemDetail(e, 'The requirement list could not be saved.');
      if (status(e) === 400) {
        this.editor.reject(detail);
        this.error.set('The list was not saved. Correct the rows marked below and save again.');
      } else {
        this.failed(e, detail);
      }
    } finally {
      this.busy.set(false);
    }
  }

  openApprove(): void {
    this.approveOpen.set(true);
  }

  async approve(): Promise<void> {
    const v = this.version();
    if (this.busy()) { return; }
    this.busy.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.api.approveRequirements(v.id));
      this.approved.set(true);
      this.approveOpen.set(false);
      this.notice.emit({ summary: 'Requirements approved', detail: `${this.requirements()?.length ?? 0} requirements for ${this.jurisdictionName()} are approved and locked.` });
      this.changed.emit();
    } catch (e: unknown) {
      this.approveOpen.set(false);
      this.failed(e, problemDetail(e, 'The requirements could not be approved.'));
    } finally {
      this.busy.set(false);
    }
  }

  /** The dialog gives focus back to the button that opened it, or to the version heading once the button is gone. */
  approveClosed(): void {
    requestAnimationFrame(() => (this.approveButton()?.nativeElement ?? document.getElementById('version-heading'))?.focus());
  }

  /** Loads the list for a newly shown version, and again when its count or approval moved, unless it is being edited. */
  private follow(v: RuleVersionView): void {
    const key = `${v.id}|${v.requirementCount}|${v.approval?.at ?? ''}`;
    const sameVersion = this.loadedFor.startsWith(`${v.id}|`);
    if (sameVersion && (key === this.loadedFor || this.editing())) { return; }
    if (!sameVersion) {
      this.editing.set(false);
      this.requirements.set(null);
      this.error.set(null);
    }
    this.loadedFor = key;
    this.load(v);
  }

  private async load(v: RuleVersionView): Promise<void> {
    try {
      const view = await firstValueFrom(this.api.requirements(v.id));
      if (this.version().id === v.id) { this.apply(view, v); }
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The requirements could not be loaded.'));
    }
  }

  private apply(view: RequirementsView, v: RuleVersionView): void {
    this.requirements.set(view.requirements);
    this.approved.set(view.approved);
    // The screen refreshes the version after a save; the list for that count is already here.
    this.loadedFor = `${v.id}|${view.requirements.length}|${view.approved ? (v.approval?.at ?? 'approved') : ''}`;
  }

  /** A 409 means someone approved the list meanwhile: say so and show the list as it now is. */
  private failed(e: unknown, detail: string): void {
    this.error.set(detail);
    if (status(e) === 409) {
      this.editing.set(false);
      this.load(this.version());
      this.changed.emit();
    }
  }
}

function status(e: unknown): number | null {
  return e instanceof HttpErrorResponse ? e.status : null;
}
