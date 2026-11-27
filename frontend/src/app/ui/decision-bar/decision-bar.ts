import { Component, ElementRef, Injector, afterNextRender, inject, input, model, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { phosphorNotePencil } from '@ng-icons/phosphor-icons/regular';
import auraTextarea from '@primeuix/themes/aura/textarea';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Textarea } from 'primeng/textarea';
import { DecisionKind } from '../../core/models';
import { registerComponentTokens } from '../../theme/component-tokens';
import { lexvayMessage } from '../../theme/message-tokens';

export const DECISION_BAR_ICONS = { phosphorNotePencil };

/**
 * The floating action bar of Review (DESIGN.md "DecisionBar"): Accept (A), Request (R) and Gap (G) with their key
 * hints on a raised, blurred surface. Gap, its key and the note button open a note field in place; with the note open
 * the third action reads Confirm gap, and every action sends the note along. The screen owns the note and its error
 * (it knows when a gap still lacks its reason); the bar lays them out and reports which action was chosen.
 *   <lx-decision-bar [(note)]="note" [(open)]="noteOpen" [busy]="busy()" [error]="noteError()" (decide)="decide($event)" />
 */
@Component({
  selector: 'lx-decision-bar',
  imports: [FormsModule, NgIcon, ButtonDirective, ButtonLabel, Message, Textarea],
  viewProviders: [provideIcons(DECISION_BAR_ICONS)],
  templateUrl: './decision-bar.html',
  styleUrl: './decision-bar.css',
  host: { '[class.lx-bar-open]': 'open()' },
})
export class DecisionBar {
  private readonly injector = inject(Injector);

  readonly note = model('');
  /** Whether the note field is open. */
  readonly open = model(false);
  readonly busy = input(false);
  readonly error = input<string | null>(null);
  readonly placeholder = input('');
  readonly decide = output<DecisionKind>();

  private readonly field = viewChild<ElementRef<HTMLTextAreaElement>>('field');
  private readonly gapButton = viewChild<ElementRef<HTMLButtonElement>>('gapButton');

  constructor() {
    registerComponentTokens({ message: lexvayMessage, textarea: auraTextarea });
  }

  /** Opens the note (Gap, G, the note button) and puts the cursor in it. */
  openNote(): void {
    this.open.set(true);
    afterNextRender(() => this.field()?.nativeElement.focus(), { injector: this.injector });
  }

  toggleNote(): void {
    if (this.open()) {
      this.open.set(false);
    } else {
      this.openNote();
    }
  }

  /** Escape in an empty note closes it and returns focus to Gap; a note with words in it stays open. */
  closeIfEmpty(event: Event): void {
    if (this.note().trim() === '') {
      event.stopPropagation();
      this.open.set(false);
      afterNextRender(() => this.gapButton()?.nativeElement.focus(), { injector: this.injector });
    }
  }
}
