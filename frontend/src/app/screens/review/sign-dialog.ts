import { DatePipe } from '@angular/common';
import { Component, ElementRef, Injector, afterNextRender, computed, effect, inject, input, linkedSignal, model, output, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { RadioButton } from 'primeng/radiobutton';
import { OUTCOME_LABEL } from '../../core/labels';
import { Determination, Outcome } from '../../core/models';
import { Seal } from '../../ui/seal';

const OUTCOMES: readonly Outcome[] = ['MEETS', 'DOES_NOT_MEET', 'NOT_DETERMINED'];

/**
 * The signing dialog (DESIGN.md "Review"): the three outcomes as a radio list, one line each, beside the line-art seal the
 * signer's name would carry, and Sign. Only a person chooses the outcome; nothing is
 * preselected. Once the screen reports the determination it recorded (`signed`), the dialog shows that seal, then
 * Done. The preview is hidden from assistive technology: until the server has signed, nothing is signed.
 */
@Component({
  selector: 'lx-sign-dialog',
  imports: [DatePipe, FormsModule, ButtonDirective, ButtonLabel, Dialog, RadioButton, Seal],
  templateUrl: './sign-dialog.html',
  styleUrl: './sign-dialog.css',
})
export class SignDialog {
  private readonly injector = inject(Injector);

  readonly open = model(false);
  readonly stateName = input.required<string>();
  readonly total = input.required<number>();
  readonly signerName = input.required<string>();
  readonly busy = input(false);
  /** The determination this dialog just recorded; null until the server has signed. */
  readonly signed = input<Determination | null>(null);
  readonly sign = output<Outcome>();

  readonly outcomes = OUTCOMES;
  readonly outcomeLabel = OUTCOME_LABEL;
  /** Every opening starts with no outcome chosen and today's date on the preview. */
  readonly outcome = linkedSignal<boolean, Outcome | null>({ source: this.open, computation: () => null });
  readonly today = linkedSignal<boolean, string>({ source: this.open, computation: () => new Date().toISOString() });
  readonly header = computed(() => (this.signed() ? 'Signed' : `Sign ${this.stateName()}`));

  private readonly doneButton = viewChild<ElementRef<HTMLButtonElement>>('done');

  constructor() {
    // The Sign button is gone once the seal appears: Done takes the focus, so the keyboard never falls to the page.
    effect(() => {
      if (this.signed()) {
        untracked(() => afterNextRender(() => this.doneButton()?.nativeElement.focus(), { injector: this.injector }));
      }
    });
  }

  submit(): void {
    const outcome = this.outcome();
    if (outcome && !this.busy()) {
      this.sign.emit(outcome);
    }
  }
}
