import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { Message } from 'primeng/message';
import { Mapping, RuleVersionView } from '../../core/models';
import { Clamp } from '../../ui/clamp';
import { DecisionState, FindingLabel } from '../../ui/finding';
import { InfoTip } from '../../ui/info-tip';
import { checkMethod } from './review-workspace';

const CHIP: Record<Mapping['finding'], string> = { EVIDENCE_FOUND: 'found', AMBIGUOUS: 'ambiguous', EVIDENCE_MISSING: 'missing' };

/**
 * The hero of Review (DESIGN.md "Review"): the requirement under decision. Ref chip and title, the rule text in the
 * Document type inside a quiet inset with its source, the proposed finding as a large chip with how it was checked in
 * one line (the explanation behind an InfoTip), the model's rationale, and the current decision.
 */
@Component({
  selector: 'lx-requirement-card',
  imports: [DatePipe, NgIcon, Message, Clamp, DecisionState, FindingLabel, InfoTip],
  templateUrl: './requirement-card.html',
  styleUrl: './requirement-card.css',
})
export class RequirementCard {
  readonly mapping = input.required<Mapping>();
  readonly ruleVersion = input<RuleVersionView | null>(null);

  readonly method = computed(() => checkMethod(this.mapping()));
  readonly chip = computed(() => CHIP[this.mapping().finding]);
}
