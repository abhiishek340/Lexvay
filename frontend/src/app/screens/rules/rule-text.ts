import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { DocumentPage } from '../../core/models';
import { problemDetail } from '../../core/problem';

/** The Rule text tab: the stored pages of the version's rule text, one page at a time, as a document. */
@Component({
  selector: 'lx-rule-text',
  imports: [NgIcon, ButtonDirective, ButtonLabel, Message, Skeleton],
  template: `
    <div class="lx-rule-text">
      @if (error(); as e) { <p-message severity="error">{{ e }}</p-message> }
      @if (page(); as p) {
        <figure class="lx-document">
          <figcaption class="lx-document-head">
            <p class="lx-document-name"><ng-icon name="phosphorFileText" size="16" aria-hidden="true" />{{ p.filename }}</p>
            <dl class="lx-document-meta">
              <div><dt>Page</dt><dd class="lx-mono" data-testid="rule-text-page" aria-live="polite">{{ p.pageNumber }} of {{ p.pageCount }}</dd></div>
            </dl>
          </figcaption>
          <div class="lx-page-text lx-serif" data-testid="rule-text">{{ p.text }}</div>
        </figure>
        @if (p.pageCount > 1) {
          <nav class="lx-pager" aria-label="Rule text pages">
            <button pButton type="button" severity="secondary" [outlined]="true" size="small" [disabled]="p.pageNumber <= 1 || loading()" (click)="show(p.pageNumber - 1)">
              <ng-icon name="phosphorCaretLeft" size="14" aria-hidden="true" /><span pButtonLabel>Previous page</span>
            </button>
            <button pButton type="button" severity="secondary" [outlined]="true" size="small" [disabled]="p.pageNumber >= p.pageCount || loading()" (click)="show(p.pageNumber + 1)">
              <span pButtonLabel>Next page</span><ng-icon name="phosphorCaretRight" size="14" aria-hidden="true" />
            </button>
          </nav>
        }
      } @else if (!error()) {
        <div aria-busy="true" aria-label="Loading the rule text"><p-skeleton height="16rem" /></div>
      }
    </div>
  `,
  styles: `
    .lx-rule-text { padding: 16px 24px 24px; display: flex; flex-direction: column; gap: 12px; }
    .lx-page-text { padding: 16px 18px 18px; white-space: pre-wrap; overflow-wrap: anywhere; max-width: 75ch; color: var(--p-lx-text); }
    .lx-pager { display: flex; align-items: center; gap: 12px; }
    .lx-pager button { gap: 6px; }
    @media (max-width: 640px) { .lx-rule-text { padding: 12px 16px 20px; } }
  `,
})
export class RuleText {
  private readonly api = inject(ApiService);
  readonly documentId = input.required<string>();
  readonly page = signal<DocumentPage | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.documentId();
      untracked(() => {
        this.page.set(null);
        this.show(1, id);
      });
    });
  }

  async show(pageNumber: number, documentId = this.documentId()): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const page = await firstValueFrom(this.api.documentPage(documentId, pageNumber));
      if (documentId === this.documentId()) { this.page.set(page); }
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'The rule text could not be loaded.'));
    } finally {
      this.loading.set(false);
    }
  }
}
