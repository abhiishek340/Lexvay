import { Component, ElementRef, Injector, afterNextRender, effect, inject, model, signal, untracked, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { phosphorCopy } from '@ng-icons/phosphor-icons/regular';
import { MessageService } from 'primeng/api';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { FileUpload } from 'primeng/fileupload';
import { Message } from 'primeng/message';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ProgramContext } from '../../core/program-context';
import { registerComponentTokens } from '../../theme/component-tokens';
import { lexvayDialog } from '../../theme/dialog-tokens';
import { lexvayFileupload } from '../../theme/fileupload-tokens';
import { lexvayMessage } from '../../theme/message-tokens';
import { Seal } from '../../ui/seal';
import { CheckView, checkProblem, checkView, fileProblem } from './report-check';

/** The icons of the dialog beyond the shell's, registered by the dialog so they load with its deferred chunk. */
export const REPORT_CHECK_ICONS = { phosphorCopy };

type Phase = { readonly step: 'choose'; readonly error: string | null } | { readonly step: 'checking' }
  | { readonly step: 'done'; readonly view: CheckView };

const CHOOSE: Phase = { step: 'choose', error: null };

/**
 * "Check a file" (ROADMAP F14): drop or choose a PDF, wait while the server hashes it, then one of three results
 * (report-check.ts), each a plain heading over meta lines (DESIGN.md v3). The file is sent once and never stored.
 * Deferred by the report screen, so its dialog, file upload, icons and token sets load on first use.
 */
@Component({
  selector: 'lx-report-check-dialog',
  imports: [RouterLink, NgIcon, ButtonDirective, ButtonLabel, Dialog, FileUpload, Message, Seal],
  templateUrl: './report-check-dialog.html',
  styleUrl: './report-check-dialog.css',
  viewProviders: [provideIcons(REPORT_CHECK_ICONS)],
})
export class ReportCheckDialog {
  private readonly api = inject(ApiService);
  private readonly context = inject(ProgramContext);
  private readonly toast = inject(MessageService);
  private readonly injector = inject(Injector);

  readonly open = model(false);
  readonly phase = signal<Phase>(CHOOSE);

  private request: Subscription | null = null;
  private readonly headline = viewChild<ElementRef<HTMLElement>>('headline');

  constructor() {
    registerComponentTokens({ dialog: lexvayDialog, fileupload: lexvayFileupload, message: lexvayMessage });
    // Every opening starts at the drop zone; closing abandons a check still under way.
    effect(() => {
      if (!this.open()) { untracked(() => this.reset()); }
    });
  }

  check(file: File | undefined): void {
    if (!file) { return; }
    const problem = fileProblem(file);
    if (problem) {
      this.phase.set({ step: 'choose', error: problem });
      return;
    }
    this.phase.set({ step: 'checking' });
    this.request = this.api.checkReport(file).subscribe({
      next: (answer) => {
        this.phase.set({ step: 'done', view: checkView(answer, (code) => this.context.jurisdictionName(code)) });
        afterNextRender(() => this.headline()?.nativeElement.focus(), { injector: this.injector });
      },
      error: (e: unknown) => this.phase.set({ step: 'choose', error: checkProblem(e) }),
    });
  }

  reset(): void {
    this.request?.unsubscribe();
    this.request = null;
    this.phase.set(CHOOSE);
  }

  async copy(sha256: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(sha256);
      this.toast.add({ severity: 'success', summary: 'Copied', detail: 'The full SHA-256 is on the clipboard.' });
    } catch {
      this.toast.add({ severity: 'error', summary: 'The SHA-256 could not be copied', detail: 'Select it on the page instead.' });
    }
  }
}
