import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, linkedSignal, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon } from '@ng-icons/core';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { NUMERIC_FIELD_LABEL } from '../../core/labels';
import { NumericField } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { DocumentPicker } from '../../shared/document-picker/document-picker';
import { DocumentViewer } from '../../shared/document-viewer/document-viewer';
import { PassageSelection } from '../../shared/document-viewer/page-text';
import { Sheet } from '../../shared/sheet/sheet';
import { Enter } from '../../ui/enter';
import { InfoTip } from '../../ui/info-tip';
import { factHistory, quoteShowsValue } from './setup-model';
import { SetupStore } from './setup-store';

/**
 * The Program facts step: one row per number the program's rules check (derived from their approved requirement
 * lists), each with its newest recorded fact and the quote that backs it, earlier ones under History. Recording opens a
 * sheet: the value, a document, the passage selected on its page. The server verifies the quote against the stored
 * page (product rule 2); the screen only warns when the passage does not show the number. Facts are never changed:
 * recording again adds a newer one.
 */
@Component({
  selector: 'lx-setup-facts',
  imports: [DatePipe, FormsModule, NgIcon, ButtonDirective, ButtonLabel, InputNumber, Message, Skeleton, DocumentPicker, DocumentViewer, Sheet, Enter, InfoTip],
  templateUrl: './facts-step.html',
  styleUrl: './steps.css',
})
export class FactsStep {
  readonly store = inject(SetupStore);
  readonly announce = output<string>();
  readonly fieldLabel = NUMERIC_FIELD_LABEL;

  readonly rows = computed(() => {
    const readiness = this.store.readiness();
    const facts = this.store.facts();
    return readiness && facts ? readiness.fieldsUsed.map((field) => ({ field, ...factHistory(facts, field) })) : null;
  });
  /** The documents that can back a fact: those with text (others are listed disabled by the picker). */
  readonly documents = computed(() => this.store.documents() ?? []);

  /** The field being recorded; null while the sheet is closed. */
  readonly recording = signal<NumericField | null>(null);
  readonly value = linkedSignal<NumericField | null, number | null>({ source: this.recording, computation: () => null });
  readonly documentId = linkedSignal<NumericField | null, string | null>({ source: this.recording, computation: () => null });
  readonly selection = linkedSignal<string | null, PassageSelection | null>({ source: this.documentId, computation: () => null });
  /** The server's 400 (the quote is not on the page), under the passage. */
  readonly quoteError = linkedSignal<PassageSelection | null, string | null>({ source: this.selection, computation: () => null });
  readonly failure = linkedSignal<NumericField | null, string | null>({ source: this.recording, computation: () => null });
  readonly valueProblem = linkedSignal<NumericField | null, string | null>({ source: this.recording, computation: () => null });
  readonly busy = signal(false);
  /** A warning, never a block: the selected passage does not show the number as written. */
  readonly mismatch = computed(() => {
    const s = this.selection();
    return s !== null && !quoteShowsValue(this.value(), s.quote);
  });
  private opener: HTMLElement | null = null;

  record(field: NumericField, trigger: EventTarget | null): void {
    this.opener = trigger instanceof HTMLElement ? trigger.closest('button') : null;
    this.recording.set(field);
  }

  close(): void {
    this.recording.set(null);
    const opener = this.opener;
    this.opener = null;
    opener?.focus();
  }

  async submit(): Promise<void> {
    const key = this.recording();
    const value = this.value();
    const documentId = this.documentId();
    const selection = this.selection();
    if (!key || this.busy()) { return; }
    if (value === null || value <= 0) { this.valueProblem.set('Enter a number above zero.'); return; }
    if (!documentId || !selection) { return; }
    this.busy.set(true);
    this.quoteError.set(null);
    this.failure.set(null);
    try {
      const fact = await this.store.recordFact({ key, value, documentId, pageNumber: selection.pageNumber, quote: selection.quote });
      this.announce.emit(`${NUMERIC_FIELD_LABEL[key]} recorded as ${fact.value}, from ${fact.filename}, page ${fact.pageNumber}.`);
      this.close();
    } catch (e: unknown) {
      const message = problemDetail(e, 'The fact could not be recorded.');
      if (e instanceof HttpErrorResponse && e.status === 400) { this.quoteError.set(message); } else { this.failure.set(message); }
    } finally {
      this.busy.set(false);
    }
  }
}
