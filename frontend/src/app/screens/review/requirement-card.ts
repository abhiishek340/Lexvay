import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { Message } from 'primeng/message';
import { Tooltip } from 'primeng/tooltip';
import { Mapping, RuleVersionView } from '../../core/models';
import { Clamp } from '../../ui/clamp';
import { decisionStatus } from '../../ui/status';
import { StatusMark } from '../../ui/status-mark';
import { checkMethod } from './review-workspace';

/**
 * The requirement under decision (DESIGN.md "Review"): ref and title, the rule text in the Document type on a left rule
 * with its source, the proposed finding as a mark and a word with how it was checked in one line (the explanation in
 * that line's tooltip), the model's rationale, and the current decision.
 */
@Component({
  selector: 'lx-requirement-card',
  imports: [DatePipe, NgIcon, Message, Tooltip, Clamp, StatusMark],
  templateUrl: './requirement-card.html',
  styleUrl: './requirement-card.css',
})
export class RequirementCard {
  readonly mapping = input.required<Mapping>();
  readonly ruleVersion = input<RuleVersionView | null>(null);

  readonly method = computed(() => checkMethod(this.mapping()));
  readonly decision = computed(() => decisionStatus(this.mapping().decision?.kind ?? null, this.mapping().stale));
}
