import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { phosphorCircleDashed, phosphorMinusCircle, phosphorSealCheck, phosphorSignature } from '@ng-icons/phosphor-icons/regular';
import { ButtonDirective } from 'primeng/button';
import { Message } from 'primeng/message';
import { ProgressBar } from 'primeng/progressbar';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { OUTCOME_LABEL, licenseLabel } from '../../core/labels';
import { Determination } from '../../core/models';
import { JurisdictionRow, JurisdictionState, jurisdictionRows } from '../../core/overview';
import { ProgramContext } from '../../core/program-context';
import { registerComponentTokens } from '../../theme/component-tokens';
import { IconName } from '../../theme/icons';
import { FindingsBar } from '../../ui/findings-bar';
import { OVERVIEW_TOKENS } from './overview-theme';

/** The icons the Overview shows beyond SHELL_ICONS, provided by its route (overview.routes.ts). */
export const OVERVIEW_ICONS = { phosphorCircleDashed, phosphorMinusCircle, phosphorSealCheck, phosphorSignature };

interface StateTag { severity: 'success' | 'info' | 'warn' | 'secondary'; icon: IconName }

const STATE_TAG: Record<JurisdictionState, StateTag> = {
  SIGNED: { severity: 'success', icon: 'phosphorSealCheck' },
  READY: { severity: 'info', icon: 'phosphorSignature' },
  IN_REVIEW: { severity: 'secondary', icon: 'phosphorCircleDashed' },
  NOT_ASSESSED: { severity: 'secondary', icon: 'phosphorMinusCircle' },
};

@Component({
  selector: 'lx-overview',
  imports: [DatePipe, RouterLink, NgIcon, ButtonDirective, Message, ProgressBar, Skeleton, TableModule, Tag, FindingsBar],
  templateUrl: './overview.html',
  styleUrl: './overview.css',
})
export class Overview {
  readonly context = inject(ProgramContext);
  private readonly router = inject(Router);
  readonly programId = input.required<string>();

  outcome(d: Determination): string {
    return OUTCOME_LABEL[d.outcome];
  }

  readonly license = licenseLabel;

  stateTag(state: JurisdictionState): StateTag {
    return STATE_TAG[state];
  }

  constructor() {
    registerComponentTokens(OVERVIEW_TOKENS);
    effect(() => {
      const id = this.programId();
      untracked(() => this.context.ensure(id));
    });
  }

  readonly rows = computed(() => {
    const program = this.context.program();
    return program ? jurisdictionRows(program, this.context.assessment(), this.context.determinations()) : [];
  });

  openReview(row: JurisdictionRow): void {
    this.router.navigate(['/programs', this.programId(), 'review'], { queryParams: { jurisdiction: row.code } });
  }

  reviewProgress(row: JurisdictionRow): number {
    return row.summary && row.summary.total > 0 ? Math.round((row.summary.decided / row.summary.total) * 100) : 0;
  }
}
