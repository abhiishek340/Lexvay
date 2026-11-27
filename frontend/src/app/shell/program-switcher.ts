import { Component, computed, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import auraSelect from '@primeuix/themes/aura/select';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import { ProgramContext } from '../core/program-context';
import { registerComponentTokens } from '../theme/component-tokens';

/** About as many characters as the 280 px switcher holds at 13 px beside its chevron. */
const SWITCHER_CHARACTERS = 40;

/** `text` cut after its last whole word that fits in `max` characters, with an ellipsis; whole when it fits. */
export function truncateAtWord(text: string, max: number): string {
  if (text.length <= max) { return text; }
  const cut = text.slice(0, max - 1);
  const space = cut.lastIndexOf(' ');
  return `${(space > 0 ? cut.slice(0, space) : cut).replace(/[\s,.;:·-]+$/, '')}…`;
}

/**
 * The program switcher in the top bar (DESIGN.md "Shell"): a ghost text button with a chevron, up to 280 px wide, with
 * the program's name cut at a word when it is longer (the full name in the tooltip and the accessible name), that moves
 * to the same screen of another program. Deferred by the shell with the Select and its tokens.
 */
@Component({
  selector: 'lx-program-switcher',
  imports: [FormsModule, Select, Tooltip],
  template: `
    <p-select class="lx-program" [options]="context.programs()" optionLabel="name" optionValue="id" [ngModel]="programId()"
              (ngModelChange)="switchTo($event)" [ariaLabel]="'Program, ' + name()" size="small" appendTo="body"
              [pTooltip]="name()" tooltipPosition="bottom" [tooltipDisabled]="shortName() === name()" [showDelay]="300">
      <ng-template #selectedItem>{{ shortName() }}</ng-template>
    </p-select>
  `,
  styles: `
    :host { display: inline-flex; min-width: 0; }
    :host ::ng-deep .lx-program.p-select {
      max-width: 280px; border-color: transparent; background: transparent; box-shadow: none;
      transition: background-color var(--lx-t-fast) var(--lx-ease);
    }
    :host ::ng-deep .lx-program.p-select:not(.p-disabled):hover, :host ::ng-deep .lx-program.p-select.p-select-open { background: var(--p-lx-sunken); border-color: transparent; }
    :host ::ng-deep .lx-program .p-select-label { font-weight: 500; padding: 6px 0 6px 8px; white-space: nowrap; color: var(--p-lx-ink); }
    :host ::ng-deep .lx-program .p-select-dropdown { width: 28px; color: var(--p-lx-ink-2); }
  `,
})
export class ProgramSwitcher {
  readonly context = inject(ProgramContext);
  private readonly router = inject(Router);
  readonly programId = input<string | null>(null);
  /** The screen to keep when switching ("overview", "review", ...). */
  readonly screen = input('overview');

  readonly name = computed(() => this.context.programs().find((p) => p.id === this.programId())?.name ?? '');
  readonly shortName = computed(() => truncateAtWord(this.name(), SWITCHER_CHARACTERS));

  constructor() {
    registerComponentTokens({ select: auraSelect });
  }

  switchTo(programId: string): void {
    this.router.navigate(['/programs', programId, this.screen()]);
  }
}
