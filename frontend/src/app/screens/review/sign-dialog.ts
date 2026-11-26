import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OUTCOME_LABEL } from '../../core/labels';
import { Outcome } from '../../core/models';

export interface SignDialogData { jurisdiction: string; total: number; }

@Component({
  selector: 'lx-sign-dialog',
  imports: [FormsModule],
  template: `
    <div class="dialog" role="dialog" aria-labelledby="sign-title" aria-describedby="sign-desc">
      <h2 id="sign-title">Sign determination for {{ data.jurisdiction }}</h2>
      <p id="sign-desc">All {{ data.total }} requirements have a review decision. Your signature records a determination in your name and locks it to the rule and course versions in use today.</p>
      <fieldset>
        <legend>Outcome</legend>
        @for (o of outcomes; track o) {
          <label class="radio"><input type="radio" name="outcome" [value]="o" [(ngModel)]="outcome" /> {{ labels[o] }}</label>
        }
      </fieldset>
      <div class="actions">
        <button type="button" (click)="ref.close(null)">Cancel</button>
        <button type="button" class="primary" [disabled]="!outcome()" (click)="ref.close(outcome())">Sign</button>
      </div>
    </div>
  `,
  styles: `
    .dialog { background: var(--lx-bg); color: var(--lx-fg); border: 1px solid var(--lx-border); border-radius: 10px; padding: 1.25rem; max-width: 28rem; width: calc(100vw - 2rem); }
    fieldset { border: 1px solid var(--lx-border); border-radius: 8px; margin: 1rem 0; }
    .radio { display: block; padding: .35rem 0; }
    .actions { display: flex; justify-content: flex-end; gap: .5rem; }
  `,
})
export class SignDialog {
  readonly ref = inject<DialogRef<Outcome | null>>(DialogRef);
  readonly data = inject<SignDialogData>(DIALOG_DATA);
  readonly outcomes: Outcome[] = ['MEETS', 'DOES_NOT_MEET', 'NOT_DETERMINED'];
  readonly labels = OUTCOME_LABEL;
  readonly outcome = signal<Outcome | null>(null);
}
