import { Component, computed, effect, inject, model, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { DatePicker } from 'primeng/datepicker';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { LEGAL_STATUS_LABEL } from '../../core/labels';
import { comparableVersions, effectiveText } from '../../core/rules';
import { ENTERABLE_STATUSES, FieldProblems, MAX_RULE_TEXT, RuleVersionField, RuleVersionForm, emptyForm, formProblems, problemField, toNewRuleVersion } from './rule-version-form';

/** The order fields are checked in, which is also the order focus goes to the first one with a problem. */
const FIELD_ORDER: RuleVersionField[] = ['jurisdiction', 'licenseType', 'label', 'sourceUrl', 'legalStatus', 'effectiveFrom', 'datesBasis', 'supersedesId', 'text'];

/** "New rule version": a contributor enters the rule text; the server stores it and queues extraction (202). */
@Component({
  selector: 'lx-new-rule-version',
  imports: [FormsModule, ButtonDirective, ButtonLabel, DatePicker, Dialog, InputText, Message, Select, Textarea],
  templateUrl: './new-rule-version.html',
  styleUrl: './new-rule-version.css',
})
export class NewRuleVersionDialog {
  private readonly api = inject(ApiService);
  private readonly context = inject(ProgramContext);

  readonly visible = model(false);
  /** The id of the version just entered. */
  readonly created = output<string>();
  /** The dialog has closed, whether or not a version was entered. */
  readonly closed = output<void>();

  readonly max = MAX_RULE_TEXT;
  /** What a person entering a version can state: a rule that is still proposed, final, or in force. */
  readonly legalStatuses = ENTERABLE_STATUSES.map((value) => ({ value, label: LEGAL_STATUS_LABEL[value] }));
  readonly form = signal<RuleVersionForm>(emptyForm(''));
  readonly problems = signal<FieldProblems>({});
  readonly general = signal<string | null>(null);
  readonly busy = signal(false);

  readonly jurisdictions = computed(() => this.context.jurisdictions().map((j) => ({ code: j.code, label: `${j.name} (${j.code})` })));
  readonly supersedable = computed(() => {
    const f = this.form();
    return f.jurisdiction
      ? comparableVersions(this.context.ruleVersions(), f.jurisdiction, f.licenseType.trim(), null).map((v) => ({ id: v.id, label: `${v.label} (effective ${effectiveText(v)})` }))
      : [];
  });
  readonly textCount = computed(() => this.form().text.length.toLocaleString('en-US').replace(/,/g, ' '));

  constructor() {
    // Every opening starts from an empty form with the program's license type.
    effect(() => {
      if (!this.visible()) { return; }
      untracked(() => {
        this.form.set(emptyForm(this.context.program()?.licenseType ?? ''));
        this.problems.set({});
        this.general.set(null);
      });
    });
  }

  set<K extends RuleVersionField>(field: K, value: RuleVersionForm[K]): void {
    this.form.update((f) => {
      const next = { ...f, [field]: value };
      // A replaced version must stay one of the same jurisdiction and license type.
      if ((field === 'jurisdiction' || field === 'licenseType') && next.supersedesId
        && !comparableVersions(this.context.ruleVersions(), next.jurisdiction ?? '', next.licenseType.trim(), null).some((v) => v.id === next.supersedesId)) {
        next.supersedesId = null;
      }
      return next;
    });
    if (this.problems()[field]) {
      this.problems.update((p) => ({ ...p, [field]: undefined }));
    }
  }

  async submit(): Promise<void> {
    if (this.busy()) { return; }
    const problems = formProblems(this.form());
    this.problems.set(problems);
    this.general.set(null);
    if (Object.keys(problems).length > 0) {
      this.focusFirstProblem();
      return;
    }
    this.busy.set(true);
    try {
      const result = await firstValueFrom(this.api.createRuleVersion(toNewRuleVersion(this.form())));
      this.visible.set(false);
      this.created.emit(result.ruleVersionId);
    } catch (e: unknown) {
      const detail = problemDetail(e, 'The rule version could not be entered.');
      const field = problemField(detail);
      if (field) {
        this.problems.set({ [field]: detail });
        this.focusFirstProblem();
      } else {
        this.general.set(detail);
      }
    } finally {
      this.busy.set(false);
    }
  }

  private focusFirstProblem(): void {
    const field = FIELD_ORDER.find((f) => this.problems()[f]);
    requestAnimationFrame(() => document.getElementById(`rv-${field}`)?.focus());
  }
}
