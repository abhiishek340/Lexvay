import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, Injector, afterNextRender, computed, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NgIcon } from '@ng-icons/core';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Drawer } from 'primeng/drawer';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { Textarea } from 'primeng/textarea';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { firstValueFrom, map } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { EvidenceRequest, EvidenceResponse } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { DocumentViewer } from '../../shared/document-viewer/document-viewer';
import { PassageSelection } from '../../shared/document-viewer/page-text';
import { COMMENT_MAX, NO_TEXT_YET, documentGroups, hasEarlierDocuments, responseBody } from './answer';
import { registerEvidenceDrawerTheme } from './evidence-drawer-theme';

/**
 * Answering one evidence request: the requirement and what was asked, a program document chosen in a grouped
 * Select, the page in DocumentViewer with selection switched on, and the chosen passage with an optional comment.
 * The passage is the stored page text between the selected offsets; the server verifies it again (product rule 2).
 */
@Component({
  selector: 'lx-evidence-drawer',
  imports: [DatePipe, FormsModule, NgIcon, ButtonDirective, ButtonLabel, Drawer, Message, Select, Skeleton, Textarea, ToggleSwitch, DocumentViewer],
  templateUrl: './evidence-drawer.html',
  styleUrl: './evidence-drawer.css',
  host: { '(document:keydown.escape)': 'onEscape($event)' },
})
export class EvidenceDrawer {
  private readonly api = inject(ApiService);
  private readonly context = inject(ProgramContext);
  private readonly injector = inject(Injector);

  readonly programId = input.required<string>();
  /** The request on screen; kept by the inbox while the drawer slides out. */
  readonly request = input<EvidenceRequest | null>(null);
  readonly open = input(false);
  readonly closed = output<void>();
  readonly submitted = output<EvidenceResponse>();
  /** A 409: the request is stale, signed or no longer open; the inbox reloads. */
  readonly conflicted = output<void>();

  readonly commentMax = COMMENT_MAX;
  readonly noTextYet = NO_TEXT_YET;

  readonly documents = rxResource({
    params: () => (this.open() ? this.programId() : undefined),
    stream: ({ params }) => this.api.programDocuments(params),
  });
  /** The rule text: the only requirement field the request row does not carry. */
  readonly requirement = rxResource({
    params: () => {
      const row = this.request();
      const assessment = this.context.assessment();
      return row && assessment ? { assessmentId: assessment.id, jurisdiction: row.jurisdiction, mappingId: row.mappingId } : undefined;
    },
    stream: ({ params }) => this.api.mappings(params.assessmentId, params.jurisdiction)
      .pipe(map((view) => view.mappings.find((m) => m.id === params.mappingId)?.requirement ?? null)),
  });

  readonly includeEarlier = linkedSignal({ source: this.request, computation: () => false });
  readonly documentId = linkedSignal<EvidenceRequest | null, string | null>({ source: this.request, computation: () => null });
  readonly selection = linkedSignal<string | null, PassageSelection | null>({ source: this.documentId, computation: () => null });
  readonly comment = linkedSignal({ source: this.request, computation: () => '' });
  /** A 400 from the server (the quote is not on the page), shown under the passage. */
  readonly quoteError = linkedSignal<PassageSelection | null, string | null>({ source: this.selection, computation: () => null });
  /** A 409 or any other failure, shown above the actions. */
  readonly failure = linkedSignal<EvidenceRequest | null, string | null>({ source: this.request, computation: () => null });
  readonly busy = signal(false);

  readonly groups = computed(() => documentGroups(this.documents.value() ?? [], this.includeEarlier()));
  readonly hasEarlier = computed(() => hasEarlierDocuments(this.documents.value() ?? []));
  readonly documentsError = computed(() => (this.documents.error() ? problemDetail(this.documents.error(), 'The program documents could not be loaded.') : null));

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  constructor() {
    registerEvidenceDrawerTheme();
  }

  /** The drawer has no initial focus of its own; its heading takes it, so the trap starts inside. */
  shown(): void {
    afterNextRender(() => this.heading()?.nativeElement.focus(), { injector: this.injector });
  }

  /**
   * Escape closes the drawer only when it was pressed inside it. PrimeNG's own Escape handling listens on the whole
   * document, so Escape in the viewer's range dialog (appended to the body) would close the drawer under it too.
   */
  onEscape(event: Event): void {
    const panel = this.heading()?.nativeElement.closest('.p-drawer');
    if (this.open() && panel && event.target instanceof Node && panel.contains(event.target)) {
      this.closed.emit();
    }
  }

  visibleChange(visible: boolean): void {
    if (!visible) { this.closed.emit(); }
  }

  chooseDocument(id: string | null): void {
    this.documentId.set(id);
  }

  setIncludeEarlier(include: boolean): void {
    this.includeEarlier.set(include);
    // A document of an earlier year that is hidden again is no longer chosen.
    const chosen = this.documentId();
    if (!include && chosen && !this.groups().some((g) => g.items.some((d) => d.id === chosen))) {
      this.documentId.set(null);
    }
  }

  async submit(): Promise<void> {
    const row = this.request();
    const documentId = this.documentId();
    const selection = this.selection();
    if (!row || !documentId || !selection || this.busy()) { return; }
    this.busy.set(true);
    this.quoteError.set(null);
    this.failure.set(null);
    try {
      this.submitted.emit(await firstValueFrom(this.api.provideEvidence(row.mappingId, responseBody(documentId, selection, this.comment()))));
    } catch (e: unknown) {
      const status = e instanceof HttpErrorResponse ? e.status : 0;
      if (status === 400) {
        this.quoteError.set(problemDetail(e, 'The passage could not be verified on the stored page.'));
      } else {
        this.failure.set(problemDetail(e, 'The evidence could not be sent.'));
        if (status === 409) { this.conflicted.emit(); }
      }
    } finally {
      this.busy.set(false);
    }
  }
}
