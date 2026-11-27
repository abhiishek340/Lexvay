import { Component, ElementRef, Injector, afterNextRender, afterRenderEffect, computed, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { phosphorArrowSquareOut, phosphorCaretLeft, phosphorCaretRight, phosphorCursorText, phosphorQuotes } from '@ng-icons/phosphor-icons/regular';
import auraDialog from '@primeuix/themes/aura/dialog';
import auraInputnumber from '@primeuix/themes/aura/inputnumber';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputNumber } from 'primeng/inputnumber';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { ApiService } from '../../core/api.service';
import { DocumentPageCache } from '../../core/document-page-cache';
import { problemDetail } from '../../core/problem';
import { registerComponentTokens } from '../../theme/component-tokens';
import { CharRange, PassageSelection, isRange, ocrNote, rangeOffsets, splitForHighlight } from './page-text';

/** The icons the viewer shows beyond SHELL_ICONS; it provides them itself because several screens embed it. */
export const DOCUMENT_VIEWER_ICONS = { phosphorArrowSquareOut, phosphorCaretLeft, phosphorCaretRight, phosphorCursorText, phosphorQuotes };

/** Where the "Use this passage" toolbar sits, relative to the scrolling page body. */
interface PendingSelection { selection: PassageSelection; top: number; left: number; }

/** Room the floating toolbar needs, so it never hangs over the page body's right edge. */
const TOOLBAR_WIDTH = 168;

/**
 * One page of an ingested document in the document frame: its text exactly as stored, set in serif, with the
 * cited span marked from the stored offsets. The viewer never searches the text; the server verified the
 * quote, the viewer only shows where it is. With `selectable`, a person can choose a passage with the mouse
 * or, from the keyboard, by character range; both emit `selection`.
 */
@Component({
  selector: 'lx-document-viewer',
  imports: [FormsModule, NgIcon, ButtonDirective, ButtonLabel, Dialog, InputNumber, Message, Skeleton],
  viewProviders: [provideIcons(DOCUMENT_VIEWER_ICONS)],
  templateUrl: './document-viewer.html',
  styleUrl: './document-viewer.css',
  host: {
    '(document:selectionchange)': 'onSelectionChange()',
    '(document:keydown.escape)': 'clearSelection()',
  },
})
export class DocumentViewer {
  private readonly api = inject(ApiService);
  private readonly pages = inject(DocumentPageCache);
  private readonly injector = inject(Injector);

  readonly documentId = input.required<string>();
  readonly pageNumber = input.required<number>();
  /** Page-relative offsets of the cited span on `pageNumber`, as `Evidence` carries them. */
  readonly highlight = input<CharRange | null>(null);
  readonly selectable = input(false);
  /**
   * Moves focus to the mark once, after the cited page first renders. Set only when the person opened the
   * viewer for this quote, so a viewer that is always on screen never takes focus from where they work.
   */
  readonly focusHighlight = input(false);
  readonly selection = output<PassageSelection>();

  /** The page on screen; back to the cited page whenever the inputs change. */
  readonly current = linkedSignal(() => {
    this.documentId();
    return this.pageNumber();
  });
  readonly page = rxResource({
    params: () => ({ documentId: this.documentId(), pageNumber: this.current() }),
    stream: ({ params }) => this.pages.page(params.documentId, params.pageNumber),
  });
  readonly loaded = computed(() => (this.page.hasValue() ? this.page.value() : null));
  readonly loadError = computed(() => (this.page.error() ? problemDetail(this.page.error(), 'The page could not be loaded.') : null));
  /** Known from any page of the document, so the position and buttons stay put while the next page loads. */
  readonly pageCount = linkedSignal<{ documentId: string; count: number | undefined }, number | null>({
    source: () => ({ documentId: this.documentId(), count: this.loaded()?.pageCount }),
    computation: (source, previous) =>
      source.count ?? (previous && previous.source.documentId === source.documentId ? previous.value : null),
  });
  /** "Read by OCR, NN % confidence" for a page whose text came from OCR (F11). */
  readonly ocr = computed(() => {
    const page = this.loaded();
    return page ? ocrNote(page) : null;
  });
  readonly hasPrevious = computed(() => this.current() > 1);
  readonly hasNext = computed(() => this.pageCount() !== null && this.current() < this.pageCount()!);
  readonly fileUrl = computed(() => this.api.documentFileUrl(this.documentId()));

  private readonly onCitedPage = computed(() => this.highlight() !== null && this.loaded()?.pageNumber === this.pageNumber());
  /** The text around and inside the mark, or null when there is nothing to mark on this page. */
  readonly parts = computed(() => {
    const page = this.loaded();
    const highlight = this.highlight();
    return page && highlight && this.onCitedPage() ? splitForHighlight(page.text, highlight.charStart, highlight.charEnd) : null;
  });
  /** The stored offsets do not fit the stored page: show the page unmarked and say so. */
  readonly mismatch = computed(() => this.onCitedPage() && this.parts() === null);

  readonly pending = signal<PendingSelection | null>(null);
  readonly rangeOpen = signal(false);
  readonly rangeStart = signal<number | null>(null);
  readonly rangeEnd = signal<number | null>(null);
  readonly rangeQuote = computed(() => {
    const page = this.loaded();
    const start = this.rangeStart();
    const end = this.rangeEnd();
    return page && start !== null && end !== null && isRange(page.text, start, end) ? page.text.slice(start, end) : null;
  });

  private readonly body = viewChild<ElementRef<HTMLElement>>('body');
  private readonly pageText = viewChild<ElementRef<HTMLElement>>('pageText');
  private readonly mark = viewChild<ElementRef<HTMLElement>>('mark');
  private readonly previousButton = viewChild.required<ElementRef<HTMLButtonElement>>('previousButton');
  private readonly nextButton = viewChild.required<ElementRef<HTMLButtonElement>>('nextButton');
  private focused = false;

  constructor() {
    registerComponentTokens({ dialog: auraDialog, inputnumber: auraInputnumber });
    // After the cited page renders (and whenever the cited span changes), bring the mark into view.
    afterRenderEffect(() => {
      this.parts();
      const mark = this.mark()?.nativeElement;
      if (!mark) {
        return;
      }
      mark.scrollIntoView({ block: 'center' });
      if (this.focusHighlight() && !this.focused) {
        this.focused = true;
        mark.focus({ preventScroll: true });
      }
    });
  }

  go(delta: -1 | 1): void {
    const target = this.current() + delta;
    const count = this.pageCount();
    if (target < 1 || (count !== null && target > count)) {
      return;
    }
    const clicked = delta < 0 ? this.previousButton() : this.nextButton();
    const hadFocus = document.activeElement === clicked.nativeElement;
    this.clearSelection();
    this.current.set(target);
    // A button disabled at the first or last page would drop focus to the page body; hand it to the other one.
    const atEnd = delta < 0 ? target === 1 : target === count;
    if (hadFocus && atEnd) {
      const other = delta < 0 ? this.nextButton() : this.previousButton();
      afterNextRender(() => other.nativeElement.focus(), { injector: this.injector });
    }
  }

  /** Pointer or key released over the page text: the selection, if it lies inside the page, is the passage. */
  onSelectionEnd(): void {
    const page = this.loaded();
    const text = this.pageText()?.nativeElement;
    const body = this.body()?.nativeElement;
    const current = document.getSelection();
    if (!this.selectable() || !page || !text || !body || !current || current.rangeCount === 0) {
      return;
    }
    const range = current.getRangeAt(0);
    const offsets = rangeOffsets(text, range);
    if (!offsets) {
      this.pending.set(null);
      return;
    }
    const previous = this.pending()?.selection;
    if (previous && previous.charStart === offsets.charStart && previous.charEnd === offsets.charEnd) {
      return;
    }
    const selection: PassageSelection = { pageNumber: page.pageNumber, quote: page.text.slice(offsets.charStart, offsets.charEnd), ...offsets };
    // Placed in the scrolling page body, so it moves with the text it belongs to.
    const area = body.getBoundingClientRect();
    const box = range.getBoundingClientRect();
    const left = Math.max(8, Math.min(box.left - area.left, area.width - TOOLBAR_WIDTH));
    this.pending.set({ selection, top: box.bottom - area.top + body.scrollTop + 6, left });
    this.selection.emit(selection);
  }

  /** The toolbar disappears once the selection is gone or has left the page text. */
  onSelectionChange(): void {
    const text = this.pageText()?.nativeElement;
    const current = document.getSelection();
    if (this.pending() && (!text || !current || current.rangeCount === 0 || !rangeOffsets(text, current.getRangeAt(0)))) {
      this.pending.set(null);
    }
  }

  usePending(): void {
    const pending = this.pending();
    if (pending) {
      this.selection.emit(pending.selection);
      this.clearSelection();
    }
  }

  clearSelection(): void {
    if (this.pending()) {
      this.pending.set(null);
      document.getSelection()?.removeAllRanges();
    }
  }

  openRange(): void {
    const pending = this.pending()?.selection;
    this.rangeStart.set(pending?.charStart ?? null);
    this.rangeEnd.set(pending?.charEnd ?? null);
    this.rangeOpen.set(true);
  }

  useRange(): void {
    const page = this.loaded();
    const quote = this.rangeQuote();
    if (!page || quote === null) {
      return;
    }
    this.selection.emit({ pageNumber: page.pageNumber, quote, charStart: this.rangeStart()!, charEnd: this.rangeEnd()! });
    this.rangeOpen.set(false);
    this.clearSelection();
  }
}
