import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { ButtonDirective } from 'primeng/button';
import { Message } from 'primeng/message';
import { ProgressBar } from 'primeng/progressbar';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { OUTCOME_LABEL } from '../../core/labels';
import { Determination } from '../../core/models';
import { JurisdictionRow, JurisdictionState, jurisdictionRows } from '../../core/overview';
import { ProgramContext } from '../../core/program-context';
import { IconName } from '../../theme/icons';
import { FindingsBar } from '../../ui/findings-bar';

const STATE_TAG: Record<JurisdictionState, { severity: 'success' | 'info' | 'warn' | 'secondary'; icon: IconName }> = {
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

  stateTag(state: JurisdictionState): { severity: 'success' | 'info' | 'warn' | 'secondary'; icon: IconName } {
    return STATE_TAG[state];
  }

  constructor() {
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
