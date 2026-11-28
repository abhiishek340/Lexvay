import { Component, computed, input, linkedSignal, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { ProgramDocument } from '../../core/models';
import { NO_TEXT_YET, documentGroups, hasEarlierDocuments } from './document-groups';

/**
 * Choosing one of a program's documents to quote from (F4's picker, shared with program facts): a grouped Select,
 * "Program documents" first and then one group per course, current catalog years only unless "Include earlier catalog
 * years" is switched on, documents without text listed but disabled. The caller labels it (`labelledBy`) and owns
 * the documents and the chosen id.
 *   <lx-document-picker [documents]="docs" [loading]="loading" [(documentId)]="chosen" labelledBy="document-label" />
 * The host component registers the Select and ToggleSwitch token sets (it renders other components of its own).
 */
@Component({
  selector: 'lx-document-picker',
  imports: [FormsModule, Message, Select, ToggleSwitch],
  template: `
    @if (error(); as e) {
      <p-message severity="error" size="small">{{ e }}</p-message>
    } @else {
      <p-select [options]="groups()" [group]="true" optionLabel="label" optionValue="id" optionDisabled="disabled" [ngModel]="documentId()"
                (ngModelChange)="documentId.set($event)" [ariaLabelledBy]="labelledBy()" placeholder="Choose a document" appendTo="body"
                [loading]="loading()" [fluid]="true" emptyMessage="This program has no documents yet." data-testid="document-select">
        <ng-template #group let-group><span class="lx-picker-group">{{ group.label }}</span></ng-template>
        <ng-template #item let-option>
          <span class="lx-picker-option">
            <span>{{ option.filename }}</span>
            <span class="lx-picker-meta">{{ option.kind }}@if (option.catalogYear) {, catalog year {{ option.catalogYear }}}@if (option.disabled) {, {{ noTextYet }}}</span>
          </span>
        </ng-template>
      </p-select>
      @if (hasEarlier()) {
        <div class="lx-picker-earlier">
          <p-toggleswitch [inputId]="labelledBy() + '-earlier'" [ngModel]="includeEarlier()" (ngModelChange)="setIncludeEarlier($event)" />
          <label [for]="labelledBy() + '-earlier'">Include earlier catalog years</label>
        </div>
      }
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
    .lx-picker-group { font-size: 12px; font-weight: 600; color: var(--p-lx-ink-2); }
    .lx-picker-option { display: flex; flex-direction: column; min-width: 0; }
    .lx-picker-meta { font-size: 12px; line-height: 16px; color: var(--p-lx-ink-2); }
    .lx-picker-earlier { display: flex; align-items: center; gap: 8px; }
    .lx-picker-earlier label { font-size: 13px; }
  `,
})
export class DocumentPicker {
  readonly documents = input.required<readonly ProgramDocument[]>();
  readonly loading = input(false);
  /** A load failure, shown in place of the Select. */
  readonly error = input<string | null>(null);
  /** The id of the element that names the picker; also the prefix of the switch's id. */
  readonly labelledBy = input.required<string>();
  readonly documentId = model<string | null>(null);

  readonly noTextYet = NO_TEXT_YET;
  /** Off again whenever the documents are replaced (a new request or a new set). */
  readonly includeEarlier = linkedSignal({ source: this.documents, computation: () => false });
  readonly groups = computed(() => documentGroups(this.documents(), this.includeEarlier()));
  readonly hasEarlier = computed(() => hasEarlierDocuments(this.documents()));

  setIncludeEarlier(include: boolean): void {
    this.includeEarlier.set(include);
    // A document of an earlier year that is hidden again is no longer chosen.
    const chosen = this.documentId();
    if (!include && chosen && !this.groups().some((g) => g.items.some((d) => d.id === chosen))) {
      this.documentId.set(null);
    }
  }
}
