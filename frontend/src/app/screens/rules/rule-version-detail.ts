import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { Message } from 'primeng/message';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Tag } from 'primeng/tag';
import { licenseLabel } from '../../core/labels';
import { RuleVersionView, UserSummary } from '../../core/models';
import { ProgramContext } from '../../core/program-context';
import { RULE_STATUS, ruleVersionStatus } from '../../core/rules';
import { DocumentViewer } from '../../shared/document-viewer/document-viewer';
import { RequirementsPanel } from './requirements-panel';
import { RuleCompare } from './rule-compare';

/** A finished operation, for the screen's live region and toast. */
export interface Notice { summary: string; detail: string; severity?: 'success' | 'info' | 'warn' | 'error' }

type DetailTab = 'requirements' | 'text' | 'compare';

/** One rule version: what it is, where it stands, and its three tabs. */
@Component({
  selector: 'lx-rule-version-detail',
  imports: [DatePipe, NgIcon, Message, Tab, TabList, TabPanel, TabPanels, Tabs, Tag, DocumentViewer, RequirementsPanel, RuleCompare],
  templateUrl: './rule-version-detail.html',
  styleUrl: './rule-version-detail.css',
})
export class RuleVersionDetail {
  private readonly context = inject(ProgramContext);
  readonly version = input.required<RuleVersionView>();
  readonly all = input.required<readonly RuleVersionView[]>();
  readonly users = input.required<readonly UserSummary[]>();
  readonly notice = output<Notice>();
  /** The version's list or approval changed on the server; the screen fetches the versions again. */
  readonly changed = output<void>();

  readonly tab = signal<DetailTab>('requirements');
  readonly status = computed(() => ruleVersionStatus(this.version(), this.all()));
  readonly statusView = computed(() => RULE_STATUS[this.status()]);
  readonly jurisdictionName = computed(() => this.context.jurisdictionName(this.version().jurisdiction));
  readonly license = computed(() => licenseLabel(this.version()));
  readonly approverName = computed(() => {
    const approval = this.version().approval;
    return approval ? (this.users().find((u) => u.id === approval.by)?.displayName ?? 'a former user') : null;
  });

  selectTab(value: string | number | undefined): void {
    if (value === 'requirements' || value === 'text' || value === 'compare') { this.tab.set(value); }
  }
}
