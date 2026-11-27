import { Component, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import auraSelect from '@primeuix/themes/aura/select';
import { Select } from 'primeng/select';
import { ProgramContext } from '../core/program-context';
import { registerComponentTokens } from '../theme/component-tokens';

/**
 * The program switcher in the top bar: a pill with the program's name (cut with an ellipsis, the full name in the
 * list) that moves to the same screen of another program. Deferred by the shell with the Select and its tokens.
 */
@Component({
  selector: 'lx-program-switcher',
  imports: [FormsModule, Select],
  template: `
    <span id="program-label" class="lx-sr-only">Program</span>
    <p-select class="lx-program" [options]="context.programs()" optionLabel="name" optionValue="id" [ngModel]="programId()"
              (ngModelChange)="switchTo($event)" ariaLabelledBy="program-label" size="small" appendTo="body" panelStyleClass="lx-program-panel" />
  `,
  styles: `
    :host { display: inline-flex; min-width: 0; }
    :host ::ng-deep .lx-program.p-select {
      max-width: 15rem; border-radius: 999px; border-color: transparent; background: var(--p-lx-surface-2);
      transition: background-color var(--lx-t-1) var(--lx-ease-out);
    }
    :host ::ng-deep .lx-program.p-select:hover { background: var(--p-lx-line); border-color: transparent; }
    :host ::ng-deep .lx-program .p-select-label { font-weight: 550; padding-left: 0.875rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  `,
})
export class ProgramSwitcher {
  readonly context = inject(ProgramContext);
  private readonly router = inject(Router);
  readonly programId = input<string | null>(null);
  /** The screen to keep when switching ("overview", "review", ...). */
  readonly screen = input('overview');

  constructor() {
    registerComponentTokens({ select: auraSelect });
  }

  switchTo(programId: string): void {
    this.router.navigate(['/programs', programId, this.screen()]);
  }
}
