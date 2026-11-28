import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { Tooltip } from 'primeng/tooltip';
import { Mapping, RuleVersionView } from '../../core/models';
import { Clamp } from '../../ui/clamp';
import { decisionStatus } from '../../ui/status';
import { StatusMark } from '../../ui/status-mark';
import { checkMethod } from './review-workspace';

/**
 * The requirement under decision (DESIGN.md "Review"): ref and title, the rule text in the Document type on a left rule
 * with its source in meta type, then Finding as a section: the proposed finding (a mark and a word, how it was checked as
 * one plain line whose tooltip explains it, the model's rationale) and the decision people have recorded.
 */
@Component({
  selector: 'lx-requirement-card',
  imports: [DatePipe, Tooltip, Clamp, StatusMark],
  templateUrl: './requirement-card.html',
  styleUrl: './requirement-card.css',
})
export class RequirementCard {
  readonly mapping = input.required<Mapping>();
  readonly ruleVersion = input<RuleVersionView | null>(null);

  readonly method = computed(() => checkMethod(this.mapping()));
  readonly decision = computed(() => decisionStatus(this.mapping().decision?.kind ?? null, this.mapping().stale));
}
